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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;
import java.util.logging.Level;

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

        @Override
        public Integer call() throws Exception {
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
                System.err.println(me.getMessage());
                return me.getApiError().getStatusCode();
            } catch (javax.ws.rs.ProcessingException pe) {
                System.err.println("An error occured while processing response from server: " + pe.getMessage());
                return 99;
            }
        }

        protected abstract Integer execute(MattermostClient client) throws MattermostApiException;


        protected <T> boolean checkForApiError(ApiResponse<T> response) {
            if (response.hasError()) {
                var error = response.readError();
                if (error != null && error.getStatusCode() > 200) {
                    System.err.println(MattermostApiException.formatApiErrorMessage("Mattermost error", error));
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
        @Override
        protected Integer execute(MattermostClient client) throws MattermostApiException {
            var pager = Pager.defaultPager();
            var getUserResponse = client.getUsers(pager);
            throwOnApiError(getUserResponse);
            getUserResponse.readEntity().forEach(user -> {
                System.out.println(user.getEmail());
                var response = client.getProfileImage(user.getId());
                if (checkForApiError(response)){
                    System.err.println("Could not get profile image for user " + user.getEmail());
                }
                try {
                    Files.write(Path.of("c:\\temp\\" + user.getId() + ".png"), response.readEntity(), StandardOpenOption.CREATE);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //  client.setProfileImage(user.getId(), );
            });

            return 0;
        }
    }

    @Command(name = "whoami", aliases = {"me"}, description = "Display info on the authenticated user")
    static class GetMe extends MattermostCommand {
        @Override
        protected Integer execute(MattermostClient client) throws MattermostApiException {
            var response = client.getMe();
            throwOnApiError(response);
            System.out.println("Id = " + response.readEntity().getId());
            System.out.println("Username = " + response.readEntity().getUsername());
            System.out.println("Email = " + response.readEntity().getEmail());
            System.out.println("Last name = " + response.readEntity().getLastName());
            System.out.println("First name = " + response.readEntity().getFirstName());
            System.out.println("Nickname = " + response.readEntity().getNickname());
            System.out.println("Role = " + response.readEntity().getRoles());
            System.out.println("is Bot = " + response.readEntity().isBot());
            if ( response.readEntity().isBot()) {
                System.out.println("Bot description = " + response.readEntity().getBotDescription());
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
            System.out.println("Message successfully posted.");
            return 0;
        }
    }

}
