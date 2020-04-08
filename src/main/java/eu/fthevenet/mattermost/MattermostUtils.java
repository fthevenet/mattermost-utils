package eu.fthevenet.mattermost;

import net.bis5.mattermost.client4.MattermostClient;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.net.URI;
import java.util.concurrent.Callable;
import java.util.logging.Level;

@Command(
        name = "mattermost-utils",
        aliases = {"mu"},
        description = "A collection of utilities for Mattermost",
        subcommands = {
                MattermostUtils.SetAvatars.class,
                MattermostUtils.GetMe.class}
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
        private String token = "0000000";

        @Option(names = {"-u", "--url"}, description = "Mattermost address")
        private URI mmAddress = URI.create("http://mattermost.com");

        @Override
        public Integer call() throws Exception {
            client = new MattermostClient.MattermostClientBuilder()
                    .url(mmAddress.toString())
                    .logLevel(Level.FINE)
                    .ignoreUnknownProperties()
                    .build();
            client.setAccessToken(token);
            return execute(client);
        }

        protected abstract Integer execute(MattermostClient client);

    }

    @Command(name = "set-avatar", aliases = {"sa"}, description = "set users avatar")
    static class SetAvatars extends MattermostCommand {
        @Override
        protected Integer execute(MattermostClient client) {
            System.out.println(client.toString());
            return 0;
        }
    }

    @Command(name = "whoami", aliases = {"me"}, description = "Get info on MM instance")
    static class GetMe extends MattermostCommand {
        @Override
        protected Integer execute(MattermostClient client) {

            System.out.println( client.getMe().readEntity().getUsername());
            return 0;
        }
    }
}
