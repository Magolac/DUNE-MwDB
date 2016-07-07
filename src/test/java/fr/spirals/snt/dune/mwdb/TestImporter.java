package fr.spirals.snt.dune.mwdb;

import org.mwg.Callback;
import org.mwg.GraphBuilder;
import org.mwg.importer.ImporterPlugin;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Created by ludovicmouline on 14/06/16.
 */
public class TestImporter {
    public static void main(String[] args) throws URISyntaxException {
        /*URL f = TestImporter.class.getResource("/");
        File ff = new File(f.toURI());
        Importer.initImport();

        Graph testGraph = new GraphBuilder().build();
        testGraph.connect(new Callback<Boolean>() {
            @Override
            public void on(Boolean aBoolean) {
//                Importer.importFile(testGraph,ff);
            }
        });*/

        URL f = TestImporter.class.getResource("/");
        File ff = new File(f.toURI());

        DuneModel model = new DuneModel(new GraphBuilder().withPlugin(new ImporterPlugin()));
        Importer importer = Importer.initImport(model);


        model.graph().connect(new Callback<Boolean>() {
            @Override
            public void on(Boolean succeed) {
                if(succeed) {
                   importer.importFile(ff);
                }
            }
        });
    }
}
