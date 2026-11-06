package io.moia.aws.run.example.equil;

import org.matsim.core.config.CommandLine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.utils.io.IOUtils;

public class RunEquil {

    /** Location of the config after run.sh has synced the scenario directory (see EquilExampleJobDefinition). */
    static final String CONFIG = PrepareInput.SCENARIO + "/config.xml";

    public static void main(String[] args) throws CommandLine.ConfigurationException {
        new Controler(loadConfig(args)).run();
    }

    static Config loadConfig(String[] args) throws CommandLine.ConfigurationException {
        CommandLine cmd = new CommandLine.Builder(args).requireOptions("output").build();
        Config config = ConfigUtils.loadConfig(IOUtils.getFileUrl(CONFIG));
        config.controller().setOutputDirectory(cmd.getOptionStrict("output"));
        config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        return config;
    }
}
