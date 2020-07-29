package com.kkb.flink.demos

import scalaj.http.Http
import com.alibaba.fastjson.JSON

object HttpT {
  def main(args: Array[String]): Unit = {
    val response = Http("http://localhost:8001/api/v1/detect").param("kpi_key", "kpi.dashboard1.a").
      param("timestamp", "123123").param("value", "123.1231").asString
    println(response.body)
    val data = JSON.parseObject(response.body)
    println(data.getString("data"))
  }
}
