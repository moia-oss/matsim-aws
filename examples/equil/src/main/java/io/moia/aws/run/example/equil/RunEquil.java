package io.moia.aws.run.example.equil;

import org.matsim.core.config.CommandLine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.utils.io.IOUtils;

import java.util.Arrays;
import java.util.List;

public class RunEquil {


    public static void main(String[] args) throws CommandLine.ConfigurationException {

        List<String> cmdOptions = Arrays.asList(
                "output"
        );

        CommandLine cmd = new CommandLine.Builder(args).requireOptions(cmdOptions.toArray(new String[cmdOptions.size()])).build();
        Config config = ConfigUtils.loadConfig(IOUtils.getFileUrl("config.xml"));
        config.controller().setOutputDirectory(cmd.getOptionStrict("output"));
        config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);

        new Controler(config).run();
    }
}
