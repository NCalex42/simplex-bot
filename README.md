## What is it?
A bot for SimpleX.chat written in Java.

It currently contains modules for moderating public groups and processing groups with a local Ollama A.I. LLM, but can be easily extended with additional modules.

#### promote-bot:
- automatically promotes new users in a group from the role 'observer' to the role 'member'
- schedule can be configured
- optionally, you can define a minimum waiting time (in days) for how long new users need to wait before they get promoted

#### moderate-bot:
- can block group members for all based on their messages' content, e.g. by regex, keyword, content type (i.e. file, image, video, link, voice) or username of the author
- can moderate messages based on their content, e.g. by regex, keyword, content type (i.e. file, image, video, link, voice) or username of the author
- can report messages (i.e. log to console or send to configured contacts or groups) based on their content, e.g. by regex, keyword, content type (i.e. file, image, video, link, voice) or username of the author

#### message-quota-bot:
- can downgrade group members to 'observer' if they exceed their message quota
- there can be a message quota per hour and per day
- there can also be a spam quota (i.e. number of identical text messages) per hour and per day

#### summary-bot:
- needs local Ollama LLM
- can generate daily or weekly summaries of groups
- depending on your LLM it can generate the summary in another language than used in the group

#### translate-bot:
- needs local Ollama LLM
- can translate messages in groups to another language



## How to use it?
Each module has one or more config files in the `bot-config` folder. Each config file name begins with the module name. If you want to disable a module, just rename or move its file(s).

After entering all mandatory parameters into the config files and starting the simplex-cli in websocket mode, you can start the bot via the `.jar` file from the `export` folder.

Note: The `bot-config` folder must be located in your current working directory, which is typically the same directory as the `.jar` file.



## What else?
- requires SimpleX cli-version (tested with 6.4.1)
- requires Java >= 11
- for A.I. modules: requires local Ollama (tested with 0.7.0)
- for developers: requires `org.json`

