package semagrams

import upickle.default._
import com.raquo.laminar.api.L._

trait Property {
  type Value

  val rw: ReadWriter[Value]

  def writeValue(v: Any) = {
    rw.transform(v.asInstanceOf[Value], ujson.Value)
  }

  def readValue(sv: ujson.Value) = {
    read[Value](sv)(rw)
  }
}

trait PValue[T: ReadWriter] extends Property {
  type Value = T

  val rw = summon[ReadWriter[T]]
}

trait Entity

type EntityMap = Map[Entity, (Sprite, PropMap)]

object EntityMap {
  def apply(): EntityMap = Map[Entity, (Sprite, PropMap)]()
}

case class EntitySource(
    entities: Signal[EntityMap] => Signal[EntityMap]
) {
  def addEntities($m: Signal[EntityMap]) = {
    $m.combineWith(entities($m)).map(_ ++ _)
  }

  def withProps(props: PropMap) = EntitySource($m =>
    entities($m).map(_.view.mapValues((s, p) => (s, p ++ props)).toMap)
  )
}
