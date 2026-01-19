package net.clementlevallois.gexfvosviewerjson;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.cli.*;
import net.clementlevallois.gexfvosviewerjson.*;

public class ConvertCommand {

    /**
     * @param args the command line arguments
     * @throws org.apache.commons.cli.ParseException
     */
    public static void main(String[] args) throws ParseException, FileNotFoundException, IOException {

        Options options = new Options();

        Option from = Option.builder("f").longOpt("from")
                .argName("from")
                .hasArg()
                .required(true)
                .desc("Which file to convert from").build();
        options.addOption(from);

        // define parser
        CommandLine cmd;
        CommandLineParser parser = new BasicParser();
        HelpFormatter helper = new HelpFormatter();

        try {
            cmd = parser.parse(options, args);
            if (cmd.hasOption("f")) {
                String opt_from = cmd.getOptionValue("from");
                System.out.println("Converting from " + opt_from);

                // TODO:: Determine if it is a .json or a .gexf file
                // then branch and convert json -> gexf or gexf -> json

                VOSViewerJsonToGexf vosToGexf = new VOSViewerJsonToGexf(Path.of(opt_from));
                String convertToGexf = vosToGexf.convertToGexf();
                System.out.println(convertToGexf);

                //        String gexfFile = "C:\\Users\\levallois\\Downloads\\Vostest places.gexf";
                //        GexfToVOSViewerJson converter = new GexfToVOSViewerJson(gexfFile);
                //        converter.setMaxNumberNodes(500);
                //        converter.setTerminologyData(new Terminology());
                //        converter.getTerminologyData().setItem("Term");
                //        converter.getTerminologyData().setItems("Terms");
                //        converter.getTerminologyData().setLink("Co-occurrence");
                //        converter.getTerminologyData().setLinks("Co-occurrences");
                //        converter.getTerminologyData().setLink_strength("Number of co-occurrences");
                //        converter.getTerminologyData().setTotal_link_strength("Total number of co-occurrences");
                //        converter.setMetadataData(new Metadata());
                //        converter.getMetadataData().setAuthorCanBePlural("");
                //        converter.getMetadataData().setDescriptionOfData("Made with nocodefunctions.com");
                //        String convertToJson = converter.convertToJson();
                //        BufferedWriter bw = Files.newBufferedWriter(Path.of("result.json"), StandardCharsets.UTF_8);
                //        bw.write(convertToJson);
                //        bw.close();
                //

            }
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            helper.printHelp("Usage:", options);
            System.exit(0);
        }

    }
}

