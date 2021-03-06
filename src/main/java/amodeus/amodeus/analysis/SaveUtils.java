/* amodeus - Copyright (c) 2018, ETH Zurich, Institute for Dynamic Systems and Control */
package amodeus.amodeus.analysis;

import java.io.File;

import amodeus.amodeus.util.io.SaveFormats;
import amodeus.amodeus.util.math.GlobalAssert;
import ch.ethz.idsc.tensor.Tensor;

/** SaveUtils is not part of the public amodeus api
 * 
 * IMPORTANT:
 * if the functionality is required in amod or elsewhere, make a copy in the external repo */
public enum SaveUtils {
    ;

    /** Saves tensor in all available file formats ({@link SaveFormats} to @param saveToFolder.
     * 
     * @param tensor
     * @param name
     * @param saveToFolder
     * @throws Exception */
    public static void saveFile(Tensor tensor, String name, File saveToFolder) throws Exception {
        saveFile(tensor, name, saveToFolder, SaveFormats.values());
    }

    /** Saves tensor in selected formats from the set {@link SaveFormats} to @param saveToFolder
     * 
     * @param tensor
     * @param name
     * @param saveToFolder
     * @param formats
     * @throws Exception */
    /* package */ static void saveFile(Tensor tensor, String name, File saveToFolder, SaveFormats... formats) throws Exception {
        GlobalAssert.that(saveToFolder.isDirectory());
        File folder = createFileDir(name, saveToFolder, false);
        String formatsString = "";
        for (SaveFormats format : formats) {
            format.save(tensor, folder, name);
            formatsString += format.name() + " ";
        }
        System.out.println("Saved " + name + " in formats " + formatsString + "to " + folder);
    }

    /* package */ static File createFileDir(String name, File saveToFolder, boolean copy) {
        File folder = new File(saveToFolder, name);
        int i = 0;
        while (copy && folder.isDirectory()) {
            folder = new File(folder + " (copy)");
            ++i;
            GlobalAssert.that(i < 20);
        }
        folder.mkdir();
        GlobalAssert.that(folder.isDirectory());
        return folder;
    }

}
