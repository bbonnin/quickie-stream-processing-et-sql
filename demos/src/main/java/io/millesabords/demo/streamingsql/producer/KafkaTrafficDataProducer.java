package io.millesabords.demo.streamingsql.producer;

import com.hortonworks.registries.schemaregistry.client.SchemaRegistryClient;
import com.hortonworks.registries.schemaregistry.serdes.avro.kafka.KafkaAvroSerializer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;


import static com.hortonworks.registries.schemaregistry.serdes.avro.AvroSnapshotSerializer.SERDES_PROTOCOL_VERSION;
import static com.hortonworks.registries.schemaregistry.serdes.avro.SerDesProtocolHandlerRegistry.METADATA_ID_VERSION_PROTOCOL;

public class KafkaTrafficDataProducer extends LogProducer {

    static {
        System.setProperty(SchemaRegistryClient.Configuration.SCHEMA_REGISTRY_URL.name(), "http://localhost:9095/api/v1");
    }

    private static final String SCHEMA = "{\n" +
            "  \"type\": \"record\",\n" +
            "  \"namespace\": \"trucking\",\n" +
            "  \"name\": \"Traffic\",\n" +
            "  \"fields\": [\n" +
            "    { \"name\": \"eventTime\", \"type\": \"long\" },\n" +
            "    { \"name\": \"routeId\", \"type\": \"int\" },\n" +
            "    { \"name\": \"congestionLevel\", \"type\": \"int\" }\n" +
            "  ]\n" +
            "}";

    private static final String TOPIC = "trucking_data_traffic";
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";


    public static void main(String[] args) throws Exception {
        new KafkaTrafficDataProducer().run();
    }

    private Producer<String, Object> createProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        //props.put(ProducerConfig.CLIENT_ID_CONFIG, "KafkaTrafficProducer");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
        props.put(SERDES_PROTOCOL_VERSION, METADATA_ID_VERSION_PROTOCOL);
        return new KafkaProducer<>(props);
    }

    private void run() throws Exception {

        Schema schema = new Schema.Parser().parse(SCHEMA);

        final Producer<String, Object> producer = createProducer();

        try {
            while (true) {
                GenericRecord datum = new GenericData.Record(schema);
                datum.put("eventTime", System.currentTimeMillis());
                datum.put("routeId", 30);
                datum.put("congestionLevel", 20);

                final ProducerRecord<String, Object> record = new ProducerRecord<>(TOPIC, datum);

                RecordMetadata metadata = producer.send(record).get();

                System.out.printf("sent record(key=%s value=%s) meta(partition=%d, offset=%d)\n",
                        record.key(), record.value(), metadata.partition(), metadata.offset());

                Thread.sleep(100);
            }
        }
        finally {
            producer.flush();
            producer.close();
        }
    }

    private static byte[] datumToByteArray(Schema schema, GenericRecord datum) throws IOException {
        GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<GenericRecord>(schema);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            Encoder e = EncoderFactory.get().binaryEncoder(os, null);
            writer.write(datum, e);
            e.flush();
            byte[] byteData = os.toByteArray();

            //headers.put("protocol.id", 0x1);
            //headers.put("schema.metadata.id", schemaIdVersion.getSchemaMetadataId());
            //headers.put("schema.version", schemaIdVersion.getVersion());

            byte[] res = new byte[byteData.length+1];
            res[0] = 1;
            System.arraycopy(byteData, 0, res, 1, byteData.length);

            return byteData;
        } finally {
            os.close();
        }
    }
}
