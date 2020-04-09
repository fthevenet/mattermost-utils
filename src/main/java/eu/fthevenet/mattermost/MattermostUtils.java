/*
 *    Copyright 2020 Frederic Thevenet
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package eu.fthevenet.mattermost;

import net.bis5.mattermost.client4.ApiResponse;
import net.bis5.mattermost.client4.MattermostClient;
import net.bis5.mattermost.client4.Pager;
import net.bis5.mattermost.model.Post;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import javax.ws.rs.core.UriBuilder;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Command(
        name = "mattermost-utils",
        aliases = {"mu"},
        description = "A collection of utilities for Mattermost",
        subcommands = {
                MattermostUtils.SetAvatars.class,
                MattermostUtils.GetMe.class,
                MattermostUtils.PostMessage.class
        }
)
public class MattermostUtils implements Callable<Integer> {
    @Spec
    CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        CommandLine.usage(spec, System.out);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MattermostUtils()).execute(args);
        System.exit(exitCode);
    }

    abstract static class MattermostCommand implements Callable<Integer> {
        private MattermostClient client;
        @Option(names = {"-t", "--token"}, description = "API Access token")
        private String token = "";

        @Option(names = {"-u", "--url"}, description = "Mattermost address")
        private URI mmAddress = URI.create("http://mattermost.exemple.com");

        @Option(names = {"-v", "--verbose"}, description = "Display detailed info")
        private boolean isVerbose;

        protected final ConsoleHelper out = new ConsoleHelper();
        ;

        @Override
        public Integer call() throws Exception {
            out.setVerbose(isVerbose);
            client = new MattermostClient.MattermostClientBuilder()
                    .url(mmAddress.toString())
                    .logLevel(Level.FINE)
                    .ignoreUnknownProperties()
                    .build();
            client.setAccessToken(token);
            try {
                execute(client);
                return 0;
            } catch (MattermostApiException me) {
                out.printException(me);
                return me.getApiError().getStatusCode();
            } catch (javax.ws.rs.ProcessingException pe) {
                out.printException("An error occurred while processing response from server", pe);
                return 99;
            }
        }

        protected abstract Integer execute(MattermostClient client) throws MattermostApiException;

        protected <T> boolean checkForApiError(ApiResponse<T> response) {
            if (response.hasError()) {
                var error = response.readError();
                if (error != null && error.getStatusCode() > 200) {
                    out.printError(MattermostApiException.formatApiErrorMessage("Mattermost error", error));
                    return true;
                }
            }
            return false;
        }

        protected <T> void throwOnApiError(ApiResponse<T> response) throws MattermostApiException {
            if (response.hasError()) {
                var error = response.readError();
                if (error != null && error.getStatusCode() > 200) {
                    throw new MattermostApiException(error);
                }
            }
        }
    }

    @Command(name = "set-avatar", aliases = {"sa"}, description = "set users avatar")
    static class SetAvatars extends MattermostCommand {

        @Option(names = {"-a", "--avatar-service-url"}, description = "The URL to the avatar service")
        private URI avatarSvcAddress = URI.create("https://www.gravatar.com/avatar/");

        @Option(names = {"-f", "--force"}, description = "Force update avatar even if it had been setup by the user")
        private boolean forceUpdate = false;

        @Option(names = {"-e", "--user-emails"}, description = "List of user emails to explicitly update")
        private List<String> emails = new ArrayList<>();

        @Option(names = {"-d", "--dry-run"}, description = "Dry run: profile image won't actually be updated")
        private boolean dryRun = false;


        @Override
        protected Integer execute(MattermostClient client) throws MattermostApiException {
            var pager = Pager.defaultPager();
            var getUserResponse = client.getUsers(pager);
            throwOnApiError(getUserResponse);
            out.printMessage("Processing users");
            for (var user : getUserResponse.readEntity()
                    .stream()
                    .filter(u -> emails.isEmpty() || emails.contains(u.getEmail()))
                    .collect(Collectors.toList())) {
                if (forceUpdate || user.getLastPictureUpdate() == 0) {
                    out.printValue(user.getEmail());
                    try {
                        URL downloadUrl = UriBuilder
                                .fromUri(avatarSvcAddress)
                                .queryParam("mail", user.getEmail())
                                .queryParam("s", 128)
                                .build().toURL();
                        Path img = downloadProfileImage(downloadUrl);
                        out.printDebug("Profile image saved to " + img);
                        if (!dryRun) {
                            client.setProfileImage(user.getId(), img);
                        }else{
                            out.printMessage("[Dry run: nothing happened] ", false);
                        }
                        out.printMessage("Updated " + user.getEmail() + " with image at " + img);
                    } catch (IOException e) {
                        out.printError("Failed to recover profile image for user " + user.getEmail());
                        out.printException(e);
                    }
                }else{
                    out.printDebug("Skip update for user " + user.getEmail());
                }
            }
            return 0;
        }

        private Path downloadProfileImage(URL url) throws IOException {
            var destPath = Files.createTempFile("userImg", "");
            destPath.toFile().deleteOnExit();
            try (var sourceChannel = Channels.newChannel(url.openStream())) {
                try (var destChannel = new FileOutputStream(destPath.toFile()).getChannel()) {
                    destChannel.transferFrom(sourceChannel, 0, Long.MAX_VALUE);
                }
            }
            return destPath;
        }
    }


    @Command(name = "whoami", aliases = {"me"}, description = "Display info on the authenticated user")
    static class GetMe extends MattermostCommand {
        @Override
        protected Integer execute(MattermostClient client) throws MattermostApiException {
            var response = client.getMe();
            throwOnApiError(response);
            out.printKeyValuePair("Id", response.readEntity().getId());
            out.printKeyValuePair("Username", response.readEntity().getUsername());
            out.printKeyValuePair("Email", response.readEntity().getEmail());
            out.printKeyValuePair("Last name", response.readEntity().getLastName());
            out.printKeyValuePair("First name", response.readEntity().getFirstName());
            out.printKeyValuePair("Nickname", response.readEntity().getNickname());
            out.printKeyValuePair("Role", response.readEntity().getRoles());
            out.printKeyValuePair("is Bot", response.readEntity().isBot());
            if (response.readEntity().isBot()) {
                out.printKeyValuePair("Bot description", response.readEntity().getBotDescription());
            }
            return 0;
        }
    }

    @Command(name = "post-message", aliases = {"msg"}, description = "Post a message to a channel")
    static class PostMessage extends MattermostCommand {
        @Option(names = {"-c", "--channel-id"}, description = "Channel ID")
        private String channelId = "";

        @Option(names = {"-m", "--message-text"}, description = "Channel ID")
        private String messageText = "";

        @Override
        protected Integer execute(MattermostClient client) throws MattermostApiException {
            throwOnApiError(client.createPost(new Post(channelId, messageText)));
            out.printMessage("Message successfully posted.");
            return 0;
        }
    }

}