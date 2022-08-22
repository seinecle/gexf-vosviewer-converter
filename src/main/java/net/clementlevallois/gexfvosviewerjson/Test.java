/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.gexfvosviewerjson;

import it.uniroma1.dis.wsngroup.gexf4j.core.Gexf;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

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
        VOSViewerJsonToGexf vosToGexf = new VOSViewerJsonToGexf(Path.of("C:\\Users\\levallois\\Downloads\\VOSviewer-network_hnut tan ho.json"));
        Gexf convertToGexf = vosToGexf.convertToGexf();
    }

}
