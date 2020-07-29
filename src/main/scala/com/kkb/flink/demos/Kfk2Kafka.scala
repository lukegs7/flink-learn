package com.kkb.flink.demos

import java.util.Properties
import net.minidev.json.parser.JSONParser
import com.alibaba.fastjson.JSON
import scalaj.http.Http
import org.apache.flink.contrib.streaming.state.RocksDBStateBackend
import org.apache.flink.runtime.state.filesystem.FsStateBackend
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer011, FlinkKafkaProducer011}
import org.apache.flink.streaming.connectors.kafka.internals.KeyedSerializationSchemaWrapper
import org.apache.flink.streaming.util.serialization.SimpleStringSchema
import scala.util.parsing.json._


object Kfk2Kafka {
  def main(args: Array[String]): Unit = {
    //获取程序的入口类以及隐式转换包
    val environment: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment


    import org.apache.flink.streaming.api.CheckpointingMode
    import org.apache.flink.streaming.api.environment.CheckpointConfig
    //checkpoint配置
    environment.enableCheckpointing(100)
    environment.getCheckpointConfig.setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE)
    environment.getCheckpointConfig.setMinPauseBetweenCheckpoints(500)
    environment.getCheckpointConfig.setCheckpointTimeout(60000)
    environment.getCheckpointConfig.setMaxConcurrentCheckpoints(1)
    environment.getCheckpointConfig.enableExternalizedCheckpoints(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION)

    //2、将checkPoint保存到文件系统  将数据保存到文件系统里面去
    environment.setStateBackend(new FsStateBackend("hdfs://node01:8020/flink_state_save"))

    //3、将数据情况保存到RocksDB 里面去
    //    environment.setStateBackend(new RocksDBStateBackend("hdfs://node01:8020/flink_save_checkPoint/checkDir", true))
    import org.apache.flink.api.scala._
    val sourceTopic: String = "kpi_source"
    val sinkTopic: String = "kpi_sink"


    val source_prop = new Properties()
    source_prop.setProperty("bootstrap.servers", "localhost:9092")
    source_prop.setProperty("group.id", "con1")
    source_prop.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    source_prop.setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")


    val sink_prop = new Properties()
    sink_prop.setProperty("bootstrap.servers", "localhost:9092")
    sink_prop.setProperty("group.id", "kafka_group1")

    //创建kafka的source
    val kafkaSource = new FlinkKafkaConsumer011[String](sourceTopic, new SimpleStringSchema(), source_prop)
    val kafkaSink = new FlinkKafkaProducer011[String](sinkTopic, new KeyedSerializationSchemaWrapper(new SimpleStringSchema()), sink_prop)
    //获取到了kafka里面的数据
    val sourceStream: DataStream[String] = environment.addSource(kafkaSource)
    val data = sourceStream.map(x => process(x))
    data.addSink(kafkaSink)
    sourceStream.print()
    environment.execute()

  }

  def process(x: String): String = {
    // 先将原始数据进行反序列化，然后传入新的数据，进行序列化操作
    val json = JSON.parseObject(x)
    val kpi_key = json.getString("kpi_key")
    val timestamp = json.getBigInteger("timestamp")
    val value = json.getDouble("value")
    val response = Http("http://localhost:8001/api/v1/detect").param("kpi_key", kpi_key).
      param("timestamp", timestamp.toString).param("value", value.toString).asString
    println(response.body)
    val data = JSON.parseObject(response.body)
    println(data.getString("data"))
    data.getString("data")
  }
}

