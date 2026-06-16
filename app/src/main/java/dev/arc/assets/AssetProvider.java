package dev.arc.assets;

import java.io.File;
import java.io.InputStream;

interface AssetProvider {
    boolean has(String assetPath);

    InputStream open(String assetPath) throws Exception;

    File materialize(String assetPath) throws Exception;
}
