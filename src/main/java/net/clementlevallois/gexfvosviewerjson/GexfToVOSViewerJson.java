package net.clementlevallois.gexfvosviewerjson;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;
import java.io.File;
import java.io.FileNotFoundException;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import org.gephi.graph.api.Column;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.EdgeIterable;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.graph.api.NodeIterable;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.importer.plugin.file.ImporterGEXF;
import org.gephi.io.importer.spi.FileImporter;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

public class GexfToVOSViewerJson {

    /**
     * Gephi Toolkit (Lookup/ProjectController/ImportController/Workspace) n'est
     * pas fait pour être utilisé "pleinement" en concurrence dans la même JVM.
     * En backend web, le plus robuste est de sérialiser ces opérations.
     */
    private static final ReentrantLock GEPHI_TOOLKIT_LOCK = new ReentrantLock(true);

    private static final String MODULARITY_COLUMN_ID = "modularity_class";

    // Inputs (un seul à la fois)
    private final Path filePath;
    private final GraphModel providedGraphModel;
    private final String gexf;
    private final InputStream inputStream;

    // Options / metadata (attention : ne pas muter en parallèle d'une conversion)
    private Terminology terminologyData;
    private Metadata metadataData;
    private Integer maxNumberNodes;

    public GexfToVOSViewerJson(Path filePath) {
        this.filePath = filePath;
        this.providedGraphModel = null;
        this.gexf = null;
        this.inputStream = null;
    }

    public GexfToVOSViewerJson(GraphModel gm) {
        this.providedGraphModel = gm;
        this.filePath = null;
        this.gexf = null;
        this.inputStream = null;
    }

    public GexfToVOSViewerJson(String gexf) {
        this.gexf = gexf;
        this.filePath = null;
        this.providedGraphModel = null;
        this.inputStream = null;
    }

    public GexfToVOSViewerJson(InputStream is) {
        this.inputStream = is;
        this.filePath = null;
        this.providedGraphModel = null;
        this.gexf = null;
    }

    public void setMaxNumberNodes(Integer maxNumberNodes) {
        this.maxNumberNodes = maxNumberNodes;
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

    public String convertToJson() {
        // Cas où vous fournissez déjà un GraphModel : on ne touche pas au cycle de vie Gephi
        if (providedGraphModel != null) {
            return convertLoadedGraphToJson(providedGraphModel);
        }

        // Sinon on charge via Gephi ImportController dans un workspace dédié, puis on ferme proprement.
        LoadedGephiContext ctx = null;
        GEPHI_TOOLKIT_LOCK.lock();
        try {
            ctx = loadIntoNewWorkspace();
            if (ctx == null || ctx.graphModel == null) {
                return "";
            }
            return convertLoadedGraphToJson(ctx.graphModel);
        } finally {
            try {
                if (ctx != null) {
                    ctx.close();
                }
            } finally {
                GEPHI_TOOLKIT_LOCK.unlock();
            }
        }
    }

    /**
     * Construit le JSON VOSviewer à partir d'un GraphModel déjà chargé. Aucun
     * accès aux API "Project/Workspace" ici, donc thread-safe au niveau du
     * Toolkit.
     */
    private String convertLoadedGraphToJson(GraphModel gm) {
        final Graph graph = gm.getGraph();

        // Builders JSON (locaux, pas d'état partagé)
        JsonObjectBuilder vosviewerJson = Json.createObjectBuilder();
        JsonObjectBuilder network = Json.createObjectBuilder();
        JsonObjectBuilder config = Json.createObjectBuilder();

        JsonArrayBuilder items = Json.createArrayBuilder();
        JsonArrayBuilder links = Json.createArrayBuilder();
        JsonArrayBuilder clusters = Json.createArrayBuilder();

        // Gestion max nodes
        boolean keepAll = true;
        Set<Node> nodesToKeep = null;
        if (maxNumberNodes != null && graph.getNodeCount() > maxNumberNodes) {
            keepAll = false;
            nodesToKeep = listNodesToKeep(graph, maxNumberNodes);
        }

        // Templates
        TemplateItem templateItemData = new TemplateItem();
        TemplateLink templateLinkData = new TemplateLink();

        // Terminology / metadata
        if (metadataData != null) {
            vosviewerJson.add("metadata", metadataData.getMetadata());
        }
        if (terminologyData != null) {
            config.add("terminology", terminologyData.getTerminology());
            templateItemData.setDescriptionHeading(terminologyData.getItem());
            templateLinkData.setDescriptionHeading(terminologyData.getLink());
        }

        // Items + Links + Clusters (Option A)
        boolean graphHasModularityColumn = gm.getNodeTable().hasColumn(MODULARITY_COLUMN_ID);

        addItems(gm, graph, items, templateItemData, keepAll, nodesToKeep, graphHasModularityColumn);
        addLinks(gm, graph, links, templateLinkData, keepAll, nodesToKeep);

        if (graphHasModularityColumn) {
            addClustersFromModularity(graph, clusters);
            network.add("clusters", clusters);
        }

        network.add("items", items);
        network.add("links", links);
        vosviewerJson.add("network", network);

        JsonObjectBuilder templates = Json.createObjectBuilder()
                .add("item_description", templateItemData.fullDescriptionItem())
                .add("link_description", templateLinkData.fullDescriptionLink());
        config.add("templates", templates);

        vosviewerJson.add("config", config);

        return writeJsonObjectBuilderToString(vosviewerJson);
    }

    private LoadedGephiContext loadIntoNewWorkspace() {
        ProjectController projectController = null;
        ImportController importController = null;
        GraphController graphController = null;

        Container container = null;

        try {
            projectController = Lookup.getDefault().lookup(ProjectController.class);
            importController = Lookup.getDefault().lookup(ImportController.class);
            graphController = Lookup.getDefault().lookup(GraphController.class);

            projectController.newProject();
            Workspace workspace = projectController.getCurrentWorkspace();

            if (filePath != null) {
                File file = filePath.toFile();
                container = importController.importFile(file);
            } else if (inputStream != null) {
                FileImporter fi = new ImporterGEXF();
                container = importController.importFile(inputStream, fi);
            } else if (gexf != null) {
                FileImporter fi = new ImporterGEXF();
                container = importController.importFile(new StringReader(gexf), fi);
            } else {
                return null;
            }

            if (container == null) {
                return null;
            }

            // Le process correct en Toolkit 0.10.x
            DefaultProcessor processor = new DefaultProcessor();
            importController.process(container, processor, workspace);

            GraphModel gm = graphController.getGraphModel(workspace);
            return new LoadedGephiContext(projectController, workspace, gm);

        } catch (FileNotFoundException ex) {
            Exceptions.printStackTrace(ex);
            System.out.println("error when loading file path in gexf vv converter");
            return null;
        } catch (Exception e) {
            System.out.println("error in network converter");
            System.out.println("malformed gexf in GexfToVOSViewerJson");
            return null;
        } finally {
            if (container != null) {
                try {
                    container.closeLoader();
                } catch (Exception ignore) {
                }
            }
        }
    }

    private void addItems(
            GraphModel gm,
            Graph graph,
            JsonArrayBuilder items,
            TemplateItem templateItemData,
            boolean keepAll,
            Set<Node> nodesToKeep,
            boolean graphHasModularityColumn
    ) {
        // Préparer template description pour attributs String
        StringBuilder sbItems = new StringBuilder();

        // Déterminer colonnes numériques candidates aux weights (non négatives pour tous les nodes)
        Set<String> numericCandidateIds = new HashSet<>();
        List<Column> columns = new ArrayList<>();
        for (Column c : gm.getNodeTable()) {
            columns.add(c);
            if (c.getTypeClass() == String.class) {
                // On utilise l'ID comme clé JSON => placeholder stable
                sbItems.append(c.getId()).append(": {").append(c.getId()).append("}, ");
            }
            if (!MODULARITY_COLUMN_ID.equals(c.getId()) && c.isNumber()) {
                numericCandidateIds.add(c.getId());
            }
        }

        if (!sbItems.toString().isBlank()) {
            sbItems.delete(sbItems.length() - 2, sbItems.length());
            templateItemData.setDescriptionText(sbItems.toString());
        }

        // Filtrer les candidates : doivent être (Number >= 0) sur tous les nodes non filtrés
        Set<String> weightsColumnIds = new HashSet<>(numericCandidateIds);
        if (!weightsColumnIds.isEmpty()) {
            NodeIterable nodes = graph.getNodes();
            for (Node n : nodes) {
                if (!keepAll && nodesToKeep != null && !nodesToKeep.contains(n)) {
                    continue;
                }
                Iterator<String> it = new HashSet<>(weightsColumnIds).iterator();
                while (it.hasNext()) {
                    String colId = it.next();
                    Object v = n.getAttribute(colId);
                    if (!(v instanceof Number num)) {
                        weightsColumnIds.remove(colId);
                        continue;
                    }
                    // exclure NaN/Inf et négatifs
                    double d = num.doubleValue();
                    if (!Double.isFinite(d) || d < 0d) {
                        weightsColumnIds.remove(colId);
                    }
                }
                if (weightsColumnIds.isEmpty()) {
                    // IMPORTANT: Break detected, unlock manually
                    nodes.doBreak();
                    break;
                }
            }
        }

        // Randomiser coords si tous identiques (souvent 0,0 partout)
        if (graph.getNodeCount() > 1 && allNodesShareSameCoordinates(graph)) {
            randomizeCoordinates(graph, 20f);
        }

        // Construire items
        NodeIterable nodes = graph.getNodes();
        for (Node node : nodes) {
            if (!keepAll && nodesToKeep != null && !nodesToKeep.contains(node)) {
                continue;
            }

            JsonObjectBuilder itemBuilder = Json.createObjectBuilder();
            JsonObjectBuilder weightsBuilder = Json.createObjectBuilder();
            JsonObjectBuilder scoresBuilder = Json.createObjectBuilder();

            String id = String.valueOf(node.getId());
            itemBuilder.add("id", id);

            String label = node.getLabel();
            itemBuilder.add("label", (label == null || label.isBlank()) ? id : label);

            // Attributs de table
            for (Column c : columns) {
                String colId = c.getId();
                Object v = node.getAttribute(colId);
                if (v == null) {
                    continue;
                }

                // modularity -> cluster (1-based)
                if (graphHasModularityColumn && MODULARITY_COLUMN_ID.equals(colId) && v instanceof Integer mc) {
                    itemBuilder.add("cluster", mc + 1);
                    continue;
                }

                if (v instanceof String s) {
                    itemBuilder.add(colId, s);
                    continue;
                }

                if (v instanceof Number num) {
                    if (weightsColumnIds.contains(colId)) {
                        addNumber(weightsBuilder, colId, num);
                    } else {
                        addNumber(scoresBuilder, colId, num);
                    }
                }
            }

            // Position : VOSviewer attend x/y au niveau item
            itemBuilder.add("x", (double) node.x());
            itemBuilder.add("y", (double) node.y());

            // Taille : peut être négative dans certains GEXF => scores
            scoresBuilder.add("viz_size", (double) node.size());

            itemBuilder.add("weights", weightsBuilder);
            itemBuilder.add("scores", scoresBuilder);

            items.add(itemBuilder);
        }
    }

    private void addLinks(GraphModel gm, Graph graph, JsonArrayBuilder links, TemplateLink templateLinkData, boolean keepAll, Set<Node> nodesToKeep) {
        StringBuilder sbLinks = new StringBuilder();
        List<Column> columns = new ArrayList<>();
        for (Column c : gm.getEdgeTable()) {
            columns.add(c);
            if (c.getTypeClass() == String.class && !"id".equals(c.getId()) && !"weight".equals(c.getId()) && !"label".equals(c.getId())) {
                sbLinks.append(c.getId()).append(": {").append(c.getId()).append("}, ");
            }
        }
        
        if (!sbLinks.toString().isBlank()) {
            sbLinks.delete(sbLinks.length() - 2, sbLinks.length());
            templateLinkData.setDescriptionTextSource(sbLinks.toString());
        }

        EdgeIterable edges = graph.getEdges();
        for (Edge edge : edges) {
            if (!keepAll && nodesToKeep != null
                    && (!nodesToKeep.contains(edge.getSource()) || !nodesToKeep.contains(edge.getTarget()))) {
                continue;
            }

            JsonObjectBuilder linkBuilder = Json.createObjectBuilder();
            linkBuilder.add("source_id", String.valueOf(edge.getSource().getId()));
            linkBuilder.add("target_id", String.valueOf(edge.getTarget().getId()));
            linkBuilder.add("strength", (double) edge.getWeight());
            
            for (Column c : columns) {
                String colId = c.getId();
                if ("id".equals(colId) || "weight".equals(colId) || "label".equals(colId)) {
                    continue;
                }
                Object v = edge.getAttribute(colId);
                if (v instanceof String s) {
                    linkBuilder.add(colId, s);
                } else if (v instanceof Number num) {
                    addNumber(linkBuilder, colId, num);
                }
            }

            links.add(linkBuilder);
        }
    }

    /**
     * Option A : clusters à partir des valeurs distinctes de modularity_class,
     * sans Appearance/Partition.
     */
    private void addClustersFromModularity(Graph graph, JsonArrayBuilder clusters) {
        Set<Integer> clusterIds = new HashSet<>();

        NodeIterable nodes = graph.getNodes();
        for (Node n : nodes) {
            Object v = n.getAttribute(MODULARITY_COLUMN_ID);
            if (v instanceof Integer mc) {
                clusterIds.add(mc + 1);
            }
        }

        clusterIds.stream().sorted().forEach(cid
                -> clusters.add(Json.createObjectBuilder()
                        .add("cluster", cid)
                        .add("label", String.valueOf(cid)))
        );
    }

    private Set<Node> listNodesToKeep(Graph graph, int maxNodes) {
        Set<Node> nodesToKeep = new HashSet<>(maxNodes);

        Map<Edge, Double> edgesAndWeight = new HashMap<>();
        EdgeIterable edges = graph.getEdges();
        for (Edge e : edges) {
            edgesAndWeight.put(e, (double) e.getWeight());
        }
        List<Entry<Edge, Double>> list = new ArrayList<>(edgesAndWeight.entrySet());
        list.sort(Entry.comparingByValue(Comparator.reverseOrder()));

        for (Entry<Edge, Double> entry : list) {
            Edge e = entry.getKey();
            nodesToKeep.add(e.getSource());
            nodesToKeep.add(e.getTarget());
            if (nodesToKeep.size() >= maxNodes) {
                break;
            }
        }
        return nodesToKeep;
    }

    private boolean allNodesShareSameCoordinates(Graph graph) {
        NodeIterable nodes = graph.getNodes();
        boolean first = true;
        float x0 = 0f;
        float y0 = 0f;

        for (Node n : nodes) {
            if (first) {
                x0 = n.x();
                y0 = n.y();
                first = false;
            } else {
                if (n.x() != x0 || n.y() != y0) {
                    // IMPORTANT: We are leaving early, so we must unlock manually
                    nodes.doBreak();
                    return false;
                }
            }
        }
        // 0 ou 1 node => "tous identiques" : true (utile pour votre randomize)
        return true;
    }

    private boolean hasDifferentCoordinates(Graph graph, float x0, float y0) {
        NodeIterable nodes = graph.getNodes();
        try {
            for (Node n : nodes) {
                if (n.x() != x0 || n.y() != y0) {
                    return true;
                }
            }
            return false;
        } finally {
            nodes.doBreak();
        }
    }

    private void randomizeCoordinates(Graph graph, float span) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        NodeIterable nodes = graph.getNodes();
        try {
            for (Node n : nodes) {
                n.setX(rnd.nextFloat() * span);
                n.setY(rnd.nextFloat() * span);
            }
        } finally {
            nodes.doBreak();
        }
    }

    private void addNumber(JsonObjectBuilder builder, String key, Number num) {
        // Jakarta JSON-P : on choisit des types stables
        if (num instanceof Integer i) {
            builder.add(key, i);
        } else if (num instanceof Long l) {
            builder.add(key, l);
        } else {
            builder.add(key, num.doubleValue());
        }
    }

    private String writeJsonObjectBuilderToString(JsonObjectBuilder jsBuilder) {
        Map<String, Object> configJsonWriter = new HashMap<>();
        configJsonWriter.put(JsonGenerator.PRETTY_PRINTING, true);

        JsonWriterFactory writerFactory = Json.createWriterFactory(configJsonWriter);
        Writer writer = new StringWriter();
        writerFactory.createWriter(writer).write(jsBuilder.build());
        return writer.toString();
    }

    /**
     * Contexte Gephi "workspace-scoped". Doit rester vivant pendant toute la
     * conversion, puis être fermé.
     */
    private static final class LoadedGephiContext implements AutoCloseable {

        private final ProjectController projectController;
        @SuppressWarnings("unused")
        private final Workspace workspace;
        private final GraphModel graphModel;

        private LoadedGephiContext(ProjectController pc, Workspace ws, GraphModel gm) {
            this.projectController = pc;
            this.workspace = ws;
            this.graphModel = gm;
        }

        @Override
        public void close() {
            try {
                if (projectController != null) {
                    // On ferme le projet créé pour cette conversion
                    projectController.closeCurrentWorkspace();
                    projectController.closeCurrentProject();
                }
            } catch (Exception ignore) {
            }
        }
    }
}
