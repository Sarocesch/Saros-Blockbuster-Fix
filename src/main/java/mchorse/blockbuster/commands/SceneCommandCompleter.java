package mchorse.blockbuster.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import mchorse.blockbuster.CommonProxy;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandManager;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

/**
 * Huelle um den registrierten /scene-Befehl, die die Tab-Vervollstaendigung
 * ergaenzt: clear, recordall, Szenennamen, all und cars.
 *
 * <p>CommandScene erbt von mclib und laesst sich mit build_and_deploy.bat nicht
 * neu kompilieren. Die neuen Unterbefehle werden in ActionHandler.onSceneCommand
 * abgefangen, die Vervollstaendigung kommt von hier. Ausfuehren, Rechte und
 * Beschreibung gehen unveraendert an den Originalbefehl.</p>
 */
public class SceneCommandCompleter extends CommandBase
{
    private final ICommand original;

    public SceneCommandCompleter(ICommand original)
    {
        this.original = original;
    }

    /**
     * Den Befehl im Server einmal einwickeln. Laeuft jeden Server-Tick und ist
     * danach eine einzige Map-Abfrage.
     */
    public static void install(MinecraftServer server)
    {
        if (server == null)
        {
            return;
        }

        ICommandManager manager = server.getCommandManager();

        if (!(manager instanceof CommandHandler))
        {
            return;
        }

        Map<String, ICommand> commands = ((CommandHandler) manager).getCommands();
        ICommand scene = commands.get("scene");

        if (scene == null || scene instanceof SceneCommandCompleter)
        {
            return;
        }

        commands.put("scene", new SceneCommandCompleter(scene));
    }

    @Override
    public String getName()
    {
        return this.original.getName();
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return this.original.getUsage(sender);
    }

    @Override
    public List<String> getAliases()
    {
        return this.original.getAliases();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
    {
        this.original.execute(server, sender, args);
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender)
    {
        return this.original.checkPermission(server, sender);
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index)
    {
        return this.original.isUsernameIndex(args, index);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos)
    {
        if (args.length == 1)
        {
            return getListOfStringsMatchingLastWord(args, "play", "stop", "toggle", "loop", "clear", "recordall");
        }

        String action = args[0];

        if (args.length == 2)
        {
            List<String> names = new ArrayList<String>();

            if (action.equals("clear"))
            {
                names.add("all");
            }

            if (action.equals("clear") || action.equals("play") || action.equals("stop") || action.equals("toggle")
                || action.equals("loop") || action.equals("recordall"))
            {
                try
                {
                    names.addAll(CommonProxy.scenes.sceneFiles());
                }
                catch (Throwable ignored)
                {}

                return getListOfStringsMatchingLastWord(args, names);
            }
        }

        if (args.length == 3)
        {
            if (action.equals("clear"))
            {
                return getListOfStringsMatchingLastWord(args, "cars");
            }

            if (action.equals("loop"))
            {
                return getListOfStringsMatchingLastWord(args, "true", "false");
            }
        }

        return Collections.<String>emptyList();
    }
}
