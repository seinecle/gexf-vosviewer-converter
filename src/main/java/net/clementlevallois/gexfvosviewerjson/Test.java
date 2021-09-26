/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.gexfvosviewerjson;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.gephi.graph.api.GraphModel;

/**
 *
 * @author LEVALLOIS
 */
public class Test {

    /**
     * @param args the command line arguments
     * @throws java.io.FileNotFoundException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException {
        String gexfFile = "miserables_result.gexf";
        GexfToVOSViewerJson converter = new GexfToVOSViewerJson(gexfFile);
        converter.setMaxNumberNodes(500);
        converter.setTerminologyData(new Terminology());
        converter.getTerminologyData().setItem("Term");
        converter.getTerminologyData().setItems("Terms");
        converter.getTerminologyData().setLink("Co-occurrence");
        converter.getTerminologyData().setLinks("Co-occurrences");
        converter.getTerminologyData().setLink_strength("Number of co-occurrences");
        converter.getTerminologyData().setTotal_link_strength("Total number of co-occurrences");
        converter.setMetadataData(new Metadata());
        converter.getMetadataData().setAuthorCanBePlural("");
        converter.getMetadataData().setDescriptionOfData("Made with nocodefunctions.com");
        String convertToJson = converter.convertToJson();
        BufferedWriter bw = Files.newBufferedWriter(Path.of("result.json"), StandardCharsets.UTF_8, StandardOpenOption.CREATE);
        bw.write(convertToJson);
        bw.close();
    }

}
