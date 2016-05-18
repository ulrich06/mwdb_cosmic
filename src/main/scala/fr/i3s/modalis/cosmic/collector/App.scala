package fr.i3s.modalis.cosmic.collector


import java.io.{File, PrintWriter}
import java.lang.Boolean

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import fr.i3s.modalis.cosmic.converter.OrganizationalToGraph
import fr.i3s.modalis.cosmic.nodes.ContainerNode.ContainerNodeFactory
import fr.i3s.modalis.cosmic.nodes.SensorNode.SensorNodeFactory
import fr.i3s.modalis.cosmic.organizational.sample.InfraSmartCampus
import org.mwg.core.NoopScheduler
import org.mwg.{Callback, GraphBuilder}
import play.api.libs.json.{JsArray, Json}
import spray.can.Http

import scala.concurrent.duration._

/**
  * Init the DB and the HTTP service
  *
  * @author ${user.name}
  */
object Launch extends App {
  implicit val system = ActorSystem("on-spray-can")

  val service = system.actorOf(Props[CollectorActor], "collector-service")
  implicit val timeout = Timeout(5.seconds)


  DataStorage.init(OrganizationalToGraph(InfraSmartCampus.catalog,
    GraphBuilder.
      builder().
      withScheduler(new NoopScheduler()).
      withFactory(new ContainerNodeFactory).
      withFactory(new SensorNodeFactory)
      .build()))

  IO(Http) ? Http.Bind(service, interface = "localhost", port = 11000)


}

/**
  * Just a small simulator that takes in into a SmartCampus measures file and generates
  * curl instructions to feed this application
  */
object Simulator extends App {

  val file = io.Source.fromFile("assets/TEMP_CAMPUS").mkString
  val json = Json.parse(file)
  val urlName = "http://localhost:8080/collect"
  val name = (json \ "id").get.as[String]
  val array = (json \ "values").as[JsArray]
  val data = array.value.par.map { v => SensorData(name, (v \ "value").as[String], (v \ "date").as[String]) }.seq
  val string = data.map(convert).mkString("\n")
  val writer = new PrintWriter(new File("generated/output.sh"))

  def convert(s: SensorData): String = {
    "curl -H \"Content-Type: application/json\" -X POST -d '{\"n\":\"" + s.n + "\", \"v\":\"" + s.v + "\", \"t\":\"" + s.t + "\"}' http://localhost:8080/collect"

  }

  writer.write(string)
  writer.close()


}
