package eu.ncalex42.simplexbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import eu.ncalex42.simplexbot.modules.messagequotabot.MessageQuotaBot;
import eu.ncalex42.simplexbot.modules.messagequotabot.MessageQuotaBotConstants;
import eu.ncalex42.simplexbot.modules.moderatebot.ModerateBot;
import eu.ncalex42.simplexbot.modules.moderatebot.ModerateBotConstants;
import eu.ncalex42.simplexbot.modules.promotebot.PromoteBot;
import eu.ncalex42.simplexbot.modules.promotebot.PromoteBotConstants;

public class Start {

    public static final String VERSION = "1.0.1";
    private static final String CONFIG_DIRECTORY = "bot-config";

    public static void main(String[] args) {

        Util.log("SimpleX-bot " + VERSION + " started at " + TimeUtil.formatUtcTimeStamp() + " ヽ(♡‿♡)ノ", null, null,
                null);

        final List<Runnable> modules = initModules();

        Util.log("Found " + modules.size() + " module(s) in "
                + Path.of(System.getProperty("user.dir"), CONFIG_DIRECTORY), null, null, null);

        final List<Thread> moduleThreads = startModules(modules);

        joinModuleThreads(moduleThreads);

        Util.log("SimpleX-bot finished at " + TimeUtil.formatUtcTimeStamp(), null, null, null);
    }

    private static List<Runnable> initModules() {

        final List<Runnable> modules = new LinkedList<>();

        try (Stream<Path> files = Files.list(Path.of(CONFIG_DIRECTORY))) {

            for (final Path filePath : files.collect(Collectors.toList())) {

                switch (filePath.getFileName().toString()) {

                case PromoteBotConstants.CFG_FILE_NAME:
                    modules.add(PromoteBot.init(filePath));
                    break;

                case ModerateBotConstants.CFG_FILE_NAME:
                    modules.add(ModerateBot.init(filePath));
                    break;

                case MessageQuotaBotConstants.CFG_FILE_NAME:
                    modules.add(MessageQuotaBot.init(filePath));
                    break;

                // you can add your custom modules here ...

                default: // skip file
                }
            }

        } catch (final Exception ex) {
            Util.logError("Failed to initialize SimpleX-bot!", null, null, null);
            Util.logError(ex.toString(), null, null, null);
            ex.printStackTrace();
            return List.of();
        }

        return modules;
    }

    private static List<Thread> startModules(List<Runnable> modules) {

        final List<Thread> threadList = new LinkedList<>();
        for (final Runnable module : modules) {
            final Thread thread = new Thread(module, module.getClass().getSimpleName());
            threadList.add(thread);
            thread.start();
        }
        return threadList;
    }

    private static void joinModuleThreads(List<Thread> moduleThreads) {

        for (final Thread moduleThread : moduleThreads) {
            try {
                moduleThread.join();
            } catch (final InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }
}
