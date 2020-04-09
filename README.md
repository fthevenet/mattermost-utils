# mattermost-utils
[![Build Status](https://dev.azure.com/binjr/Misc/_apis/build/status/fthevenet.mattermost-utils?branchName=master)](https://dev.azure.com/binjr/Misc/_build/latest?definitionId=7&branchName=master)

A collection of utilities for Mattermost

###Usage

Build or download the mattermost-utils jar file and start with the following command line, using Java 11 or higher:
```
java -jar mattermost-utils-x.x.jar
```

Append the name of a command and the required parameters:

```
Usage: mattermost-utils [-hV] [COMMAND]
A collection of utilities for Mattermost
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.
Commands:
  set-avatar, sa     Update users' profile pictures from an avatar service.
  who-am-i, me       Display info on the authenticated user.
  post-message, msg  Post a message to a channel.
```

The following commands are available:

#####set-avatar
```
Usage: mattermost-utils set-avatar [-dfhvV] [-a=<avatarServiceUrl>]
                                   [-t=<token>] [-u=<mattermostUrl>]
                                   [-e=<emails>]...
set users avatar
  -a, --avatar-service-url=<avatarServiceUrl>
                        The URL to the avatar service
  -d, --dry-run         Dry run: profile images won't actually be updated
  -e, --user-emails=<emails>
                        List of user emails to explicitly update
  -f, --force           Force update avatar even if it had been setup by the
                          user
  -h, --help            Show this help message and exit.
  -t, --token=<token>   API Access token
  -u, --url=<mattermostUrl>
                        Mattermost address
  -v, --verbose         Display detailed info
  -V, --version         Print version information and exit.
```

#####who-am-i
```
Usage: mattermost-utils who-am-i [-hvV] [-t=<token>] [-u=<mattermostUrl>]
Display info on the authenticated user.
  -h, --help            Show this help message and exit.
  -t, --token=<token>   API Access token
  -u, --url=<mattermostUrl>
                        Mattermost address
  -v, --verbose         Display detailed info
  -V, --version         Print version information and exit.
```

#####post-message
```
Usage: mattermost-utils post-message [-hvV] [-c=<channelId>] [-m=<messageText>]
                                     [-t=<token>] [-u=<mattermostUrl>]
Post a message to a channel.
  -c, --channel-id=<channelId>
                        Channel ID
  -h, --help            Show this help message and exit.
  -m, --message-text=<messageText>
                        Channel ID
  -t, --token=<token>   API Access token
  -u, --url=<mattermostUrl>
                        Mattermost address
  -v, --verbose         Display detailed info
  -V, --version         Print version information and exit.
```
