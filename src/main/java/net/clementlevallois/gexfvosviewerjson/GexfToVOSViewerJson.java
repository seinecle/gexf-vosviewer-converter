/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.gexfvosviewerjson;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.gephi.appearance.api.AppearanceController;
import org.gephi.appearance.api.AppearanceModel;
import org.gephi.appearance.api.Function;
import org.gephi.appearance.api.Partition;
import org.gephi.appearance.api.PartitionFunction;
import org.gephi.appearance.plugin.PartitionElementColorTransformer;
import org.gephi.graph.api.Column;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.ContainerUnloader;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.importer.plugin.file.ImporterGEXF;
import org.gephi.io.importer.spi.FileImporter;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.project.api.ProjectController;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 * @author LEVALLOIS
 */
public class GexfToVOSViewerJson {

    /**
     * @param args the command line arguments
     */
    JsonObjectBuilder vosviewerJson;
    JsonObjectBuilder network;
    JsonObjectBuilder metadata;
    JsonArrayBuilder items;
    JsonArrayBuilder links;
    JsonArrayBuilder clusters;
    JsonObjectBuilder config;
    JsonObjectBuilder terminology;
    Path filePath;
    GraphModel gm;
    boolean graphHasModularityColumn = false;
    Terminology terminologyData;
    Metadata metadataData;
    TemplateItem templateItemData;
    TemplateLink templateLinkData;
    Integer maxNumberNodes;
    Set<Node> nodesToKeep = new HashSet();
    boolean keepAll = false;
    InputStream is;
    String gexf;

    public GexfToVOSViewerJson(Path filePath) {
        this.filePath = filePath;
    }

    public GexfToVOSViewerJson(GraphModel gm) {
        this.gm = gm;
    }

    public GexfToVOSViewerJson(String gexf) {
        this.gexf = gexf;
    }

    public GexfToVOSViewerJson(InputStream is) {
        this.is = is;
    }

    public String convertToJson() throws FileNotFoundException {
        if (gm == null) {
            load();
        }
        if (gm == null){
            return "";
        }
        vvJsonInitiate();

        if (maxNumberNodes != null && gm.getGraph().getNodeCount() > maxNumberNodes) {
            listNodesToKeep(maxNumberNodes);
        }

        if (maxNumberNodes != null && gm.getGraph().getNodeCount() <= maxNumberNodes) {
            keepAll = true;
        }

        templateItemData = new TemplateItem();
        templateLinkData = new TemplateLink();
        addItems();
        addLinks();
        if (metadataData != null) {
            vosviewerJson.add("metadata", metadataData.getMetadata());
        }
        if (terminologyData != null) {
            config.add("terminology", terminologyData.getTerminology());
            templateItemData.setDescriptionHeading(terminologyData.getItem());
            templateLinkData.setDescriptionHeading(terminologyData.getLink());
        }

        JsonObjectBuilder templates = Json.createObjectBuilder();
        templates.add("item_description", templateItemData.fullDescriptionItem());
        templates.add("link_description", templateLinkData.fullDescriptionLink());
        config.add("templates", templates);

        if (graphHasModularityColumn) {
            addClusters();
        }
        vosviewerJson.add("network", network);
        vosviewerJson.add("config", config);

        String string = writeJsonObjectBuilderToString(vosviewerJson);

        return string;
    }

    private void load() {
        ProjectController projectController = Lookup.getDefault().lookup(ProjectController.class);
        GraphController graphController = Lookup.getDefault().lookup(GraphController.class);
        ImportController importController = Lookup.getDefault().lookup(ImportController.class);
        projectController.newProject();
        Container container = null;
        if (filePath != null) {
            try {
                File file = filePath.toFile();
                container = importController.importFile(file);
                container.closeLoader();
            } catch (FileNotFoundException ex) {
                Exceptions.printStackTrace(ex);
                System.out.println("error when loading file path in gexf vv converter");
                return;
            }
        } else if (is != null) {
            FileImporter fi = new ImporterGEXF();
            container = importController.importFile(is, fi);
            container.closeLoader();
        } else if (gexf != null) {
            FileImporter fi = new ImporterGEXF();
            container = importController.importFile(new StringReader(gexf), fi);
            container.closeLoader();
        }
        DefaultProcessor processor = new DefaultProcessor();
        processor.setWorkspace(projectController.getCurrentWorkspace());
        processor.setContainers(new ContainerUnloader[]{container.getUnloader()});
        processor.process();
        gm = graphController.getGraphModel();
    }

    private void vvJsonInitiate() {
        vosviewerJson = Json.createObjectBuilder();
        network = Json.createObjectBuilder();
        metadata = Json.createObjectBuilder();
        config = Json.createObjectBuilder();
        terminology = Json.createObjectBuilder();
        items = Json.createArrayBuilder();
        links = Json.createArrayBuilder();
        clusters = Json.createArrayBuilder();
    }

    private void addItems() {
        JsonObjectBuilder itemBuilder;
        JsonObjectBuilder weightsBuilder;
        JsonObjectBuilder scoresBuilder;

        String modularityColumnName = "modularity_class";

        //Preparing the description template for item attributes:
        StringBuilder sbItems = new StringBuilder();

        // collecting all node attributes in a set which will correspond to "weight" in vosviewer json.
        Set<String> nodeAttributesThatCanBeWeight = new HashSet();
        int countNodeColumns = gm.getNodeTable().countColumns();
        for (int i = 0; i < countNodeColumns; i++) {
            Column nodeColumn = gm.getNodeTable().getColumn(i);
            if (!nodeColumn.getId().equals("modularity_class") && nodeColumn.isNumber()) {
                nodeAttributesThatCanBeWeight.add(nodeColumn.getId());
            }
            if (nodeColumn.getId().equals("modularity_class")) {
                graphHasModularityColumn = true;
            }
            if (nodeColumn.getTypeClass() == String.class) {
                sbItems.append(nodeColumn.getId()).append(": {").append(nodeColumn.getId()).append("}, ");
            }
        }
        if (!sbItems.toString().isBlank()) {
            sbItems.delete(sbItems.length() - 2, sbItems.length()); // to remove the last comma
            templateItemData.setDescriptionText(sbItems.toString());
        }

        // removing node attributes from the set if they have any negative value, because "weight" in vosviewer json accepts only non negative values.
        Object[] toArray = gm.getGraph().getNodes().toCollection().toArray();
        for (Object nodeObject : toArray) {
            Node node = (Node) nodeObject;
            Iterator<String> nodeAttributeKeysIterator = node.getAttributeKeys().iterator();
            while (nodeAttributeKeysIterator.hasNext()) {
                String nodeAttributeKey = nodeAttributeKeysIterator.next();

                Object attribute = node.getAttribute(nodeAttributeKey);
                if (attribute instanceof Number) {
                    Number attNumber = (Number) attribute;
                    if (attNumber instanceof Double && (double) attNumber < 0) {
                        nodeAttributesThatCanBeWeight.remove(nodeAttributeKey);
                    }
                    if (attNumber instanceof Short && (short) attNumber < 0) {
                        nodeAttributesThatCanBeWeight.remove(nodeAttributeKey);
                    }
                    if (attNumber instanceof Float && (float) attNumber < 0) {
                        nodeAttributesThatCanBeWeight.remove(nodeAttributeKey);
                    }
                    if (attNumber instanceof Integer && (int) attNumber < 0) {
                        nodeAttributesThatCanBeWeight.remove(nodeAttributeKey);
                    }
                    if (attNumber instanceof Long && (long) attNumber < 0) {
                        nodeAttributesThatCanBeWeight.remove(nodeAttributeKey);
                    }
                } else {
                    nodeAttributesThatCanBeWeight.remove(nodeAttributeKey);
                }

            }
        }

        // iterating through all nodes and check whether they have zero as a value for x and y coordinates: in this case it needs to be randomized
        int counterZeroForX = 0;
        int counterZeroForY = 0;
        float pastX = 0;
        float pastY = 0;
        int count = 0;
        for (Object nodeObject : toArray) {
            Node node = (Node) nodeObject;
            if (node.x() == pastX) {
                counterZeroForX++;
            } else {
                pastX = node.x();
            }
            if (node.y() == pastY) {
                counterZeroForY++;
            } else {
                pastY = node.y();
            }
        }

        if (counterZeroForX + 1 >= toArray.length && counterZeroForY + 1 >= toArray.length) {
            for (Object nodeObject : toArray) {
                Node node = (Node) nodeObject;
                node.setX((float) Math.random() * 20);
                node.setY((float) Math.random() * 20);
            }
        }

        // iterating through all nodes and their attributes and creating the corresponding items and values in vosviewer json
        for (Object nodeObject : toArray) {
            Node node = (Node) nodeObject;
            if (maxNumberNodes != null && !keepAll && !nodesToKeep.contains(node)) {
                continue;
            }
            itemBuilder = Json.createObjectBuilder();
            weightsBuilder = Json.createObjectBuilder();
            scoresBuilder = Json.createObjectBuilder();

            itemBuilder.add("id", (String) node.getId());
            if (node.getLabel() == null || node.getLabel().isBlank()) {
                itemBuilder.add("label", (String) node.getId());
            } else {
                itemBuilder.add("label", node.getLabel());
            }

            Iterator<Column> nodeAttributeColumnsIterator = node.getAttributeColumns().iterator();
            while (nodeAttributeColumnsIterator.hasNext()) {
                Column nodeAttributeColumn = nodeAttributeColumnsIterator.next();
                String nodeAttributeKey = nodeAttributeColumn.getId();
                String nodeAttributeTitle = nodeAttributeColumn.getTitle();

                switch (nodeAttributeKey) {
                    case "x":
                    case "y":
                        itemBuilder.add(nodeAttributeKey, (Float) node.getAttribute(nodeAttributeKey));
                        break;
                    default:
                        if (node.getAttribute(nodeAttributeKey) instanceof String) {
                            itemBuilder.add(nodeAttributeTitle, (String) node.getAttribute(nodeAttributeKey));
                        } else if (nodeAttributesThatCanBeWeight.contains(nodeAttributeKey)) {
                            Object attribute = node.getAttribute(nodeAttributeKey);
                            if (attribute instanceof Double) {
                                Double value = (Double) attribute;
                                weightsBuilder.add(nodeAttributeTitle, value);
                            }
                            if (attribute instanceof Float) {
                                weightsBuilder.add(nodeAttributeTitle, (Float) attribute);
                            }
                            if (attribute instanceof Long) {
                                weightsBuilder.add(nodeAttributeTitle, (Long) attribute);
                            }
                            if (attribute instanceof Integer && !nodeAttributeKey.equals(modularityColumnName)) {
                                weightsBuilder.add(nodeAttributeTitle, (Integer) attribute);
                            }
                        } else {
                            Object attribute = node.getAttribute(nodeAttributeKey);
                            if (attribute instanceof Double) {
                                scoresBuilder.add(nodeAttributeTitle, (Double) attribute);
                            }
                            if (attribute instanceof Float) {
                                scoresBuilder.add(nodeAttributeTitle, (Float) attribute);
                            }
                            if (attribute instanceof Long) {
                                scoresBuilder.add(nodeAttributeTitle, (Long) attribute);
                            }
                            if (attribute instanceof Integer && !nodeAttributeKey.equals(modularityColumnName)) {
                                scoresBuilder.add(nodeAttributeTitle, (Integer) attribute);
                            }
                            // if the attribute corresponds to the modularity_class column in Gephi, this should convert to the "cluster" attribute in vosviewer.
                            // +1 because the Gephi communities are zero-based, vosviewer clusters are 1-based.
                            if (attribute instanceof Integer && graphHasModularityColumn && nodeAttributeKey.equals(modularityColumnName)) {
                                itemBuilder.add("cluster", (Integer) attribute + 1);
                            }
                        }
                        break;
                }
            }

            // size of the node is supposed to be a positive number, so it should be included in "weights"
            // but in practice, gexf files can include nodes with a negative size ->  <viz:size value="-0.71428585"></viz:size>
            // these rare cases break the parsing of the VOSviewer json file
            // so we include the node size attribute in scores, which can be negative
            scoresBuilder.add("viz_size", node.size());

            if (!node.getAttributeKeys().contains("x") && !node.getAttributeKeys().contains("y")) {
                itemBuilder.add("x", (Float) node.x());
                itemBuilder.add("y", (Float) node.y());
            }
            itemBuilder.add("weights", weightsBuilder);
            itemBuilder.add("scores", scoresBuilder);
            items.add(itemBuilder);
        }
        network.add("items", items);
    }

    private String writeJsonObjectBuilderToString(JsonObjectBuilder jsBuilder) {
        Map<String, Boolean> configJsonWriter = new HashMap();
        configJsonWriter.put(JsonGenerator.PRETTY_PRINTING, true);
        JsonWriterFactory writerFactory = Json.createWriterFactory(configJsonWriter);
        Writer writer = new StringWriter();
        writerFactory.createWriter(writer).write(jsBuilder.build());

        String json = writer.toString();
        return json;
    }

    private void addLinks() {
        JsonObjectBuilder linkBuilder;

        // iterating through all edges and creating the corresponding links and values in vosviewer json
        Object[] toArray = gm.getGraph().getEdges().toCollection().toArray();
        for (Object edgeObject : toArray) {
            Edge edge = (Edge) edgeObject;
            if (maxNumberNodes != null && !keepAll && (!nodesToKeep.contains(edge.getSource()) | !nodesToKeep.contains(edge.getTarget()))) {
                continue;
            }

            linkBuilder = Json.createObjectBuilder();

            linkBuilder.add("source_id", (String) edge.getSource().getId());
            linkBuilder.add("target_id", (String) edge.getTarget().getId());
            linkBuilder.add("strength", (float) edge.getWeight());

            links.add(linkBuilder);
        }
        network.add("links", links);
    }

    private void addClusters() {
        gm.getGraph().readUnlockAll();
        AppearanceController appearanceController = Lookup.getDefault().lookup(AppearanceController.class);
        AppearanceModel appearanceModel = appearanceController.getModel();
        Column modColumn = gm.getNodeTable().getColumn("modularity_class");
        Function func = appearanceModel.getNodeFunction(modColumn, PartitionElementColorTransformer.class);
        Partition partition = ((PartitionFunction) func).getPartition();

        // iterating through all edges and creating the corresponding links and values in vosviewer json
        Iterator<Integer> iteratorModularityValues = partition.getValues(gm.getGraph()).iterator();
        while (iteratorModularityValues.hasNext()) {
            Integer modularityClass = iteratorModularityValues.next();
            JsonObjectBuilder clusterBuilder = Json.createObjectBuilder();
            clusterBuilder.add("cluster", modularityClass);
            clusterBuilder.add("label", String.valueOf(modularityClass));

            clusters.add(clusterBuilder);
        }
        network.add("clusters", clusters);
    }

    public Terminology getTerminologyData() {
        return terminologyData;
    }

    public void setTerminologyData(Terminology terminologyData) {
        this.terminologyData = terminologyData;
    }

    public Metadata getMetadataData() {
        return metadataData;
    }

    public void setMetadataData(Metadata metadataData) {
        this.metadataData = metadataData;
    }

    private void listNodesToKeep(Integer maxNumberNodes) {
        Map<Edge, Double> edgesAndWeight = new HashMap();
        Object[] toArray = gm.getGraph().getEdges().toCollection().toArray();
        for (Object o : toArray) {
            Edge next = (Edge) o;
            edgesAndWeight.put(next, next.getWeight());
        }

        List<Entry<Edge, Double>> list = new ArrayList<>(edgesAndWeight.entrySet());
        list.sort(Entry.comparingByValue(Comparator.reverseOrder()));

        for (Entry<Edge, Double> entry : list) {
            nodesToKeep.add(entry.getKey().getSource());
            nodesToKeep.add(entry.getKey().getTarget());
            if (nodesToKeep.size() >= maxNumberNodes) {
                break;
            }
        }
    }

    public void setMaxNumberNodes(Integer maxNumberNodes) {
        this.maxNumberNodes = maxNumberNodes;
    }

}
