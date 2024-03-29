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
import net.bis5.mattermost.model.User;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import javax.ws.rs.core.UriBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Command(
        name = "mattermost-utils",
        aliases = {"mu"},
        description = "A collection of utilities for Mattermost",
        mixinStandardHelpOptions = true,
        version = MattermostUtils.VERSION,
        subcommands = {
                MattermostUtils.SetAvatars.class,
                MattermostUtils.GetMe.class,
                MattermostUtils.PostMessage.class
        }
)
public class MattermostUtils implements Callable<Integer> {
    public static final String VERSION = "mattermost- utils - 0.1";
    @Spec
    CommandSpec spec;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MattermostUtils()).execute(args);
        System.exit(exitCode);
    }

    public static void downloadTo(URL url, File destPath) throws IOException {
        try (var sourceChannel = Channels.newChannel(url.openStream())) {
            try (var destChannel = new FileOutputStream(destPath).getChannel()) {
                destChannel.transferFrom(sourceChannel, 0, Long.MAX_VALUE);
            }
        }
    }

    public static boolean populateListFromFile(List<String> list, Path path) {
        if (path != null) {
            try {
                return list.addAll(Files.readAllLines(path, StandardCharsets.UTF_8));
            } catch (IOException e) {
                Console.io.printException(e);
            }
        }
        return false;
    }

    @Override
    public Integer call() throws Exception {
        CommandLine.usage(spec, System.out);
        return 0;
    }

    @Command(name = "download-files",
            aliases = {"dl"},
            description = "Download a series of files",
            mixinStandardHelpOptions = true,
            version = MattermostUtils.VERSION)
    public int downloadFiles(
            @Option(names = {"-u", "--template-url"}, description = "Template URL") String templateUrl,
            @Option(names = {"-o", "--output-template"}, description = "Output template") String output,
            @Option(names = {"-f", "--from"}, description = "From", defaultValue = "0") int from,
            @Option(names = {"-t", "--To"}, description = "To", defaultValue = "1") int to,
            @Option(names = {"-v", "--verbose"}, description = "Display detailed info") boolean isVerbose) {
        Console.io.setVerbose(isVerbose);
        for (int i = from; i < to; i++) {
            try {
                var url = new URL(templateUrl
                        .replace("{i}", Integer.toString(i))
                        .replace("{i+1}", Integer.toString(i + 1)));
                var destination = Path.of(output
                        .replace("{i}", Integer.toString(i))
                        .replace("{i+1}", Integer.toString(i + 1)));
                Console.io.printKeyValuePair(url.toString(), destination.toString());
                downloadTo(url, destination.toFile());
            } catch (IOException e) {
                Console.io.printException(e);
            }
        }
        return 0;
    }

    abstract static class MattermostCommand implements Callable<Integer> {
        private MattermostClient client;
        @Option(names = {"-t", "--token"}, description = "API Access token")
        private String token = "";

        @Option(names = {"-u", "--url"}, description = "Mattermost address")
        private URI mattermostUrl = URI.create("http://mattermost.exemple.com");

        @Option(names = {"-v", "--verbose"}, description = "Display detailed info")
        private boolean isVerbose;

        @Override
        public Integer call() throws Exception {
            Console.io.setVerbose(isVerbose);
            client = new MattermostClient.MattermostClientBuilder()
                    .url(mattermostUrl.toString())
                    .logLevel(Level.FINE)
                    .httpConfig(clientBuilder -> {
                        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows" )) {
                            try {
                                KeyStore tks = KeyStore.getInstance("Windows-ROOT");
                                tks.load(null, null);
                                clientBuilder.trustStore(tks);
                            } catch (KeyStoreException | CertificateException | IOException |
                                     NoSuchAlgorithmException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    })
                    .ignoreUnknownProperties()
                    .build();
            client.setAccessToken(token);
            try {
                execute(client);
                return 0;
            } catch (MattermostApiException me) {
                Console.io.printException(me);
                return me.getApiError().getStatusCode();
            } catch (javax.ws.rs.ProcessingException pe) {
                Console.io.printException("An error occurred while processing response from server", pe);
                return 102;
            } catch (IllegalArgumentException e) {
                Console.io.printException("Invalid argument", e);
                return 101;
            } catch (Exception e) {
                Console.io.printException("Unexpected error", e);
                return 100;
            }
        }

        protected abstract Integer execute(MattermostClient client) throws MattermostApiException;

        protected <T> boolean checkForApiError(ApiResponse<T> response) {
            if (response.hasError()) {
                var error = response.readError();
                if (error != null && error.getStatusCode() > 200) {
                    Console.io.printError(MattermostApiException.formatApiErrorMessage("Mattermost error", error));
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

    @Command(name = "set-avatar", aliases = {"sa"},
            description = "Update users' profile pictures from an avatar service.",
            mixinStandardHelpOptions = true,
            version = MattermostUtils.VERSION)
    static class SetAvatars extends MattermostCommand {

        @Option(names = {"-a", "--avatar-service-url"}, description = "The URL to the avatar service")
        private URI avatarServiceUrl;

        @Option(names = {"-f", "--force"}, description = "Force update avatar even if it had been setup by the user")
        private boolean forceUpdate = false;

        @Option(names = {"-i", "--include-user"}, description = "User names to explicitly update")
        private List<String> includedUsernames = new ArrayList<>();

        @Option(names = {"-x", "--exclude-user"}, description = "User names to explicitly exclude from the update")
        private List<String> excludedUsernames = new ArrayList<>();

        @Option(names = {"--excludelist"}, description = "List of user names to explicitly exclude from the update")
        private Path excludeListPath;

        @Option(names = {"--includelist"}, description = "List of user names to explicitly update")
        private Path includeListPath;

        @Option(names = {"-d", "--dry-run"}, description = "Dry run: profile images won't actually be updated")
        private boolean dryRun = false;

        @Override
        protected Integer execute(MattermostClient client) throws MattermostApiException {
            populateListFromFile(excludedUsernames, excludeListPath);
            populateListFromFile(includedUsernames, includeListPath);
            var users = new ArrayList<User>();
            for (var pager = Pager.defaultPager(); fetchUsers(client, pager, users); pager = pager.nextPage()) {
                Console.io.printDebug("Processing users (page " + pager.getPage() + ")");
                users.stream()
                        .filter(u -> !excludedUsernames.contains(u.getUsername())
                                && (includedUsernames.isEmpty() || includedUsernames.contains(u.getUsername())))
                        .forEach(user -> {
                            if (forceUpdate || user.getLastPictureUpdate() == 0) {
                                Console.io.printValue(user.getUsername());
                                try {
                                    URL downloadUrl = UriBuilder
                                            .fromUri(avatarServiceUrl)
                                            .path(user.getUsername())
                                            .build().toURL();
                                    var imgPath = Files.createTempFile("userImg", "");
                                    imgPath.toFile().deleteOnExit();
                                    downloadTo(downloadUrl, imgPath.toFile());
                                    Console.io.printDebug("Profile image saved to " + imgPath);
                                    if (!dryRun) {
                                        if (!checkForApiError(client.setProfileImage(user.getId(), imgPath))) {
                                            Console.io.printMessage("Updated " + user.getUsername() + " with image at " + imgPath);
                                        }
                                    } else {
                                        Console.io.printMessage("[Dry run: nothing happened] Updated " +
                                                user.getUsername() +
                                                " with image at " + imgPath);
                                    }
                                } catch (IOException e) {
                                    Console.io.printError("Failed to recover profile image for user " + user.getUsername());
                                    Console.io.printException(e);
                                }
                            } else {
                                Console.io.printDebug("Skip update for user " + user.getUsername());
                            }
                        });
            }
            return 0;
        }

        private boolean fetchUsers(MattermostClient client, Pager pager, List<User> users) throws MattermostApiException {
            var getUserResponse = client.getUsers(pager);
            throwOnApiError(getUserResponse);
            users.clear();
            users.addAll(getUserResponse.readEntity());
            return !users.isEmpty();
        }
    }


    @Command(name = "who-am-i",
            aliases = {"me"},
            description = "Display info on the authenticated user.",
            mixinStandardHelpOptions = true,
            version = MattermostUtils.VERSION)
    static class GetMe extends MattermostCommand {
        @Override
        protected Integer execute(MattermostClient client) throws MattermostApiException {
            var response = client.getMe();
            throwOnApiError(response);
            Console.io.printKeyValuePair("Id", response.readEntity().getId());
            Console.io.printKeyValuePair("Username", response.readEntity().getUsername());
            Console.io.printKeyValuePair("Email", response.readEntity().getEmail());
            Console.io.printKeyValuePair("Last name", response.readEntity().getLastName());
            Console.io.printKeyValuePair("First name", response.readEntity().getFirstName());
            Console.io.printKeyValuePair("Nickname", response.readEntity().getNickname());
            Console.io.printKeyValuePair("Role", response.readEntity().getRoles());
            Console.io.printKeyValuePair("is Bot", response.readEntity().isBot());
            if (response.readEntity().isBot()) {
                Console.io.printKeyValuePair("Bot description", response.readEntity().getBotDescription());
            }
            return 0;
        }
    }

    @Command(name = "post-message",
            aliases = {"msg"},
            description = "Post a message to a channel.",
            mixinStandardHelpOptions = true,
            version = MattermostUtils.VERSION)
    static class PostMessage extends MattermostCommand {
        @Option(names = {"-c", "--channel-id"}, description = "Channel ID")
        private String channelId = "";

        @Option(names = {"-m", "--message-text"}, description = "Channel ID")
        private String messageText = "";

        @Override
        protected Integer execute(MattermostClient client) throws MattermostApiException {
            throwOnApiError(client.createPost(new Post(channelId, messageText)));
            Console.io.printMessage("Message successfully posted.");
            return 0;
        }
    }

}
