package com.orientechnologies.orient.core.storage.impl.local.paginated.wal.co.indexengine;

import com.orientechnologies.common.serialization.types.OByteSerializer;
import com.orientechnologies.common.serialization.types.OIntegerSerializer;
import com.orientechnologies.common.serialization.types.OStringSerializer;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.serialization.serializer.binary.OBinarySerializerFactory;
import com.orientechnologies.orient.core.storage.impl.local.OAbstractPaginatedStorage;
import com.orientechnologies.orient.core.storage.impl.local.paginated.wal.WALRecordTypes;
import com.orientechnologies.orient.core.storage.impl.local.paginated.wal.co.OComponentOperationRecord;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class OIndexEngineCreateCO extends OComponentOperationRecord {
  private String engineName;
  private String algorithm;
  private String indexType;

  private byte keySerializerId;
  private byte valueSerializerId;

  private boolean             isAutomatic;
  private int                 version;
  private int                 apiVersion;
  private boolean             multiValue;
  private Map<String, String> engineProperties;
  private Set<String>         clustersToIndex;
  private ODocument           metadata;

  private int     keySize;
  private OType[] keyTypes;

  private boolean nullValuesSupport;

  private int indexId;

  public OIndexEngineCreateCO() {
  }

  public OIndexEngineCreateCO(final String engineName, final String algorithm, final String indexType, final byte keySerializerId,
      final byte valueSerializerId, final boolean isAutomatic, final int version, final int apiVersion, final boolean multiValue,
      final Map<String, String> engineProperties, final Set<String> clustersToIndex, final ODocument metadata, final int keySize,
      final OType[] keyTypes, final boolean nullValuesSupport) {
    this.engineName = engineName;
    this.algorithm = algorithm;
    this.indexType = indexType;
    this.keySerializerId = keySerializerId;
    this.valueSerializerId = valueSerializerId;
    this.isAutomatic = isAutomatic;
    this.version = version;
    this.apiVersion = apiVersion;
    this.multiValue = multiValue;
    this.engineProperties = engineProperties;
    this.clustersToIndex = clustersToIndex;
    this.metadata = metadata;
    this.keySize = keySize;
    this.keyTypes = keyTypes;
    this.nullValuesSupport = nullValuesSupport;
  }

  public String getEngineName() {
    return engineName;
  }

  public String getAlgorithm() {
    return algorithm;
  }

  public String getIndexType() {
    return indexType;
  }

  public byte getKeySerializerId() {
    return keySerializerId;
  }

  public byte getValueSerializerId() {
    return valueSerializerId;
  }

  public boolean isAutomatic() {
    return isAutomatic;
  }

  public int getVersion() {
    return version;
  }

  public int getApiVersion() {
    return apiVersion;
  }

  public boolean isMultiValue() {
    return multiValue;
  }

  public Map<String, String> getEngineProperties() {
    return engineProperties;
  }

  public Set<String> getClustersToIndex() {
    return clustersToIndex;
  }

  public ODocument getMetadata() {
    return metadata;
  }

  public int getKeySize() {
    return keySize;
  }

  public OType[] getKeyTypes() {
    return keyTypes;
  }

  public boolean isNullValuesSupport() {
    return nullValuesSupport;
  }

  @Override
  public void redo(final OAbstractPaginatedStorage storage) throws IOException {
    final OBinarySerializerFactory binarySerializerFactory = OBinarySerializerFactory.getInstance();

    storage.addIndexEngineInternal(engineName, algorithm, indexType, binarySerializerFactory.getObjectSerializer(valueSerializerId),
        isAutomatic, true, version, apiVersion, multiValue, engineProperties, clustersToIndex, metadata,
        binarySerializerFactory.getObjectSerializer(keySerializerId), keySize, keyTypes, nullValuesSupport);
  }

  @Override
  public void undo(final OAbstractPaginatedStorage storage) throws IOException {
    storage.deleteIndexEngineInternal(indexId);
  }

  @Override
  protected void serializeToByteBuffer(final ByteBuffer buffer) {
    OStringSerializer.INSTANCE.serializeInByteBufferObject(engineName, buffer);
    OStringSerializer.INSTANCE.serializeInByteBufferObject(algorithm, buffer);
    OStringSerializer.INSTANCE.serializeInByteBufferObject(indexType, buffer);

    buffer.put(keySerializerId);
    buffer.put(valueSerializerId);

    buffer.put(isAutomatic ? (byte) 1 : (byte) 0);

    buffer.putInt(version);
    buffer.putInt(apiVersion);

    buffer.put(multiValue ? (byte) 1 : (byte) 0);

    if (engineProperties == null || engineProperties.isEmpty()) {
      buffer.putInt(0);
    } else {
      buffer.putInt(engineProperties.size());

      for (final Map.Entry<String, String> entry : engineProperties.entrySet()) {
        OStringSerializer.INSTANCE.serializeInByteBufferObject(entry.getKey(), buffer);
        OStringSerializer.INSTANCE.serializeInByteBufferObject(entry.getValue(), buffer);
      }
    }

    if (clustersToIndex == null || clustersToIndex.isEmpty()) {
      buffer.putInt(0);
    } else {
      buffer.putInt(clustersToIndex.size());

      for (final String clusterToIndex : clustersToIndex) {
        OStringSerializer.INSTANCE.serializeInByteBufferObject(clusterToIndex, buffer);
      }
    }



    if (metadata == null) {
      buffer.put((byte) 0);
    } else {
      buffer.put((byte) 1);
      OStringSerializer.INSTANCE.serializeInByteBufferObject(metadata.toJSON(), buffer);
    }

    buffer.putInt(keySize);

    if (keyTypes == null) {
      buffer.putInt(0);
    } else {
      buffer.putInt(keyTypes.length);
      for (final OType type : keyTypes) {
        buffer.put((byte) type.getId());
      }
    }

    buffer.put(nullValuesSupport ? (byte) 1 : (byte) 0);
  }

  @Override
  protected void deserializeFromByteBuffer(final ByteBuffer buffer) {
    engineName = OStringSerializer.INSTANCE.deserializeFromByteBufferObject(buffer);
    algorithm = OStringSerializer.INSTANCE.deserializeFromByteBufferObject(buffer);
    indexType = OStringSerializer.INSTANCE.deserializeFromByteBufferObject(buffer);

    keySerializerId = buffer.get();
    valueSerializerId = buffer.get();

    isAutomatic = buffer.get() == 1;

    version = buffer.getInt();
    apiVersion = buffer.getInt();

    multiValue = buffer.get() == 1;

    final int enginePropertiesSize = buffer.getInt();
    engineProperties = new HashMap<>(enginePropertiesSize);

    for (int i = 0; i < enginePropertiesSize; i++) {
      final String key = OStringSerializer.INSTANCE.deserializeFromByteBufferObject(buffer);
      final String value = OStringSerializer.INSTANCE.deserializeFromByteBufferObject(buffer);

      engineProperties.put(key, value);
    }

    final int clustersToIndexSize = buffer.getInt();
    clustersToIndex = new HashSet<>();

    for (int i = 0; i < clustersToIndexSize; i++) {
      clustersToIndex.add(OStringSerializer.INSTANCE.deserializeFromByteBufferObject(buffer));
    }

    if (buffer.get() == 0) {
      metadata = null;
    } else {
      final String metadataJSON = OStringSerializer.INSTANCE.deserializeFromByteBufferObject(buffer);
      metadata = new ODocument();
      metadata.fromJSON(metadataJSON);
    }

    keySize = buffer.getInt();

    final int keyTypesSize = buffer.getInt();
    keyTypes = new OType[keyTypesSize];

    for (int i = 0; i < keyTypesSize; i++) {
      final byte keyTypeId = buffer.get();
      keyTypes[i] = OType.getById(keyTypeId);
    }

    nullValuesSupport = buffer.get() == 1;
  }

  @Override
  public int serializedSize() {
    int size = OStringSerializer.INSTANCE.getObjectSize(engineName) + OStringSerializer.INSTANCE.getObjectSize(algorithm)
        + OStringSerializer.INSTANCE.getObjectSize(indexType) + 3 * OByteSerializer.BYTE_SIZE + 2 * OIntegerSerializer.INT_SIZE
        + OByteSerializer.BYTE_SIZE + OIntegerSerializer.INT_SIZE;

    if (engineProperties != null) {
      for (final Map.Entry<String, String> property : engineProperties.entrySet()) {
        size += OStringSerializer.INSTANCE.getObjectSize(property.getKey());
        size += OStringSerializer.INSTANCE.getObjectSize(property.getValue());
      }
    }

    size += OIntegerSerializer.INT_SIZE;

    if (clustersToIndex != null) {
      for (final String clusterToIndex : clustersToIndex) {
        size += OStringSerializer.INSTANCE.getObjectSize(clusterToIndex);
      }
    }

    size += OByteSerializer.BYTE_SIZE;
    if (metadata != null) {
      size += OStringSerializer.INSTANCE.getObjectSize(metadata.toJSON());
    }

    size += OIntegerSerializer.INT_SIZE;

    size += OIntegerSerializer.INT_SIZE;
    if (keyTypes != null) {
      size += keyTypes.length;
    }

    size += OByteSerializer.BYTE_SIZE;

    return super.serializedSize() + size;
  }

  @Override
  public byte getId() {
    return WALRecordTypes.INDEX_ENGINE_CREATE_CO;
  }
}