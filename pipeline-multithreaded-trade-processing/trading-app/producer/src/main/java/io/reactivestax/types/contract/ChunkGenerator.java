package io.reactivestax.types.contract;

import java.io.FileNotFoundException;
import java.util.Map;

public interface ChunkGenerator {
    Integer generateAndSubmitChunks(String filePath, Integer numberOfChunks) throws FileNotFoundException;
}
