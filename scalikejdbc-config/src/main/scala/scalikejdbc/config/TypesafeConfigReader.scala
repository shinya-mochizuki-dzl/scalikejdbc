/*
 * Copyright 2013 Toshiyuki Takahashi, Kazuhiro Sera
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package scalikejdbc.config

import scalikejdbc._
import com.typesafe.config.{ Config => TypesafeConfig, ConfigFactory, ConfigException }
import scala.collection.mutable.{ Map => MutableMap, ListBuffer }

/**
 * Configuration Exception
 */
class ConfigurationException(val message: String) extends Exception(message) {
  def this(e: Throwable) = this(e.getMessage)
}

/**
 * Typesafe Config reader
 */
object TypesafeConfigReader {

  private lazy val _config: TypesafeConfig = ConfigFactory.load()

  lazy val dbNames: List[String] = {
    val it = _config.entrySet.iterator
    val buf: ListBuffer[String] = new ListBuffer
    while (it.hasNext) {
      val entry = it.next
      val key = entry.getKey
      key.split("\\.").toList match {
        case List("db", dbName, _) => buf.append(dbName)
        case _ => ()
      }
    }
    buf.toList.distinct
  }

  def readAsMap(dbName: Symbol = ConnectionPool.DEFAULT_NAME): Map[String, String] = try {
    val dbConfig = _config.getConfig("db." + dbName.name)
    val iter = dbConfig.entrySet.iterator
    val configMap: MutableMap[String, String] = MutableMap.empty
    while (iter.hasNext) {
      val entry = iter.next()
      val key = entry.getKey
      configMap(key) = _config.getString("db." + dbName.name + "." + key)
    }
    configMap.toMap
  } catch {
    case e: ConfigException => throw new ConfigurationException(e)
  }

  def readJDBCSettings(dbName: Symbol = ConnectionPool.DEFAULT_NAME): JDBCSettings = {
    val configMap = TypesafeConfigReader.readAsMap(dbName)
    (for {
      driver <- configMap.get("driver")
      url <- configMap.get("url")
      user <- configMap.get("user")
      password <- configMap.get("password")
    } yield {
      JDBCSettings(driver, url, user, password)
    }) getOrElse {
      throw new ConfigurationException("Configuration error for database " + dbName + ". " + configMap.toString)
    }
  }

  def readConnectionPoolSettings(dbName: Symbol = ConnectionPool.DEFAULT_NAME): ConnectionPoolSettings = {
     val configMap = TypesafeConfigReader.readAsMap(dbName)
     val default = new ConnectionPoolSettings
     ConnectionPoolSettings(
       initialSize = configMap.get("poolInitialSize").map(_.toInt).getOrElse(default.initialSize),
       maxSize = configMap.get("poolMaxSize").map(_.toInt).getOrElse(default.maxSize),
       validationQuery = configMap.get("poolValidationQuery").getOrElse(default.validationQuery)
     )
  }

  def loadGlobalSettings(): Unit = Option(_config.getConfig("scalikejdbc.global")).map { globalConfig =>
    Option(globalConfig.getConfig("loggingSQLAndTime")).map { config => 
      val enabled = config.getBoolean("enabled")
      if (enabled) {
        val default = LoggingSQLAndTimeSettings()
        GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
          enabled = enabled,
          logLevel = Option(config.getString("logLevel")).map(v => Symbol(v)).getOrElse(default.logLevel),
          warningEnabled = Option(config.getString("warningEnabled")).map(_.toBoolean).getOrElse(default.warningEnabled),
          warningThresholdMillis = Option(config.getString("warningThresholdMillis")).map(_.toLong).getOrElse(default.warningThresholdMillis),
          warningLogLevel = Option(config.getString("warningLogLevel")).map(v => Symbol(v)).getOrElse(default.warningLogLevel)
        )
      } else {
        GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(enabled = false)
      }
    }
  }

}
