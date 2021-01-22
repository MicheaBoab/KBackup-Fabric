package com.keuin.kbackupfabric.backup.incremental;

import com.keuin.kbackupfabric.backup.incremental.identifier.Sha256Identifier;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

public class ObjectCollectionSerializerTest {

    @Test
    public void testSerializationConsistency1() throws IOException {
        testSerializationConsistency(1, 0);
    }

    @Test
    public void testSerializationConsistency2() throws IOException {
        testSerializationConsistency(2, 0);
    }

    @Test
    public void testSerializationConsistency4() throws IOException {
        testSerializationConsistency(4, 0);
    }

    @Test
    public void testSerializationConsistency8() throws IOException {
        testSerializationConsistency(8, 0);
    }

    @Test
    public void testSerializationConsistency1A() throws IOException {
        testSerializationConsistency(1, 1000);
    }

    @Test
    public void testSerializationConsistency2A() throws IOException {
        testSerializationConsistency(2, 1000);
    }

    @Test
    public void testSerializationConsistency4A() throws IOException {
        testSerializationConsistency(4, 1000);
    }

    @Test
    public void testSerializationConsistency8A() throws IOException {
        testSerializationConsistency(8, 1000);
    }

    public void testSerializationConsistency(int threads, int multiThreadThreshold) throws IOException {
        ObjectCollectionFactory<Sha256Identifier> factory =
                new ObjectCollectionFactory<>(Sha256Identifier.getFactory(), threads, multiThreadThreshold);
        ObjectCollection collection =
                factory.fromDirectory(new File("./testfile/ObjectCollectionFactoryTest"));
        File file = new File("./testfile/serialized");
        if (file.exists()) {
            Files.delete(file.toPath());
        }
        ObjectCollectionSerializer.toFile(collection, file);
        ObjectCollection collection2 = ObjectCollectionSerializer.fromFile(file);
        Files.delete(file.toPath());
        assertEquals(collection, collection2);
    }

}