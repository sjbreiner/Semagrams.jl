package graph

import semagrams.*
import semagrams.util.*
import semagrams.acsets.{*, given}
import semagrams.actions.*
import cats.data._
import cats.Monad
import cats.effect.IO
import cats.data.OptionT
import semagrams.sprites._
import com.raquo.laminar.api.L.{*, given}
import semagrams.controllers._

/**
 * TODO:
 * - Clean up use of OptionT[Action[PosGraph,_],A]
 * - Prettier edges
 * - Dialogue for edge creation
 *
 *
 * What do I need?
 *
 * Basically, I want something like split, but it keeps track of a map of things
 * rather than a list. I feel like this should be possible using a Var containing
 * a map, and then an updater for that Var that takes in a new list of things,
 * and then updates the map based on new entities.
 *
 * The values in the map should be the rendered sprite, and then also a signal
 * of the propmap used for that rendered sprite, along with the initial values
 * of that propmap.
 *
 * This signal of PropMaps for each entity can then be used in later entity types,
 * using Signal.combine.
 */

/**
 * A positioned graph
 */
type PosGraph = LabeledGraph[Complex]

val addBox: Action[PosGraph, Unit] = for {
  pos <- mousePos
  _ <- updateModelS(addLabeledVertex(pos))
} yield {}

val remBox: Action[PosGraph, Unit] = (for {
  v <- OptionT(hoveredPart[PosGraph, V.type])
  _ <- OptionT.liftF(updateModel[PosGraph](_.remPart(v)))
} yield {}).value.map(_ => {})

val addEdgeAction: Action[PosGraph, Unit] = (for {
  _ <- OptionT.liftF(mouseDown(MouseButton.LeftButton))
  s <- OptionT(hoveredPart[PosGraph, V.type])
  _ <- OptionT.liftF(mouseDown(MouseButton.LeftButton))
  t <- OptionT(hoveredPart[PosGraph, V.type])
  _ <- OptionT.liftF(updateModelS[PosGraph, Elt[E.type]](addEdge(s, t)))
} yield {}).value.map(_ => {})

val bindings = KeyBindings(
  Map(
    "a" -> addBox,
    "d" -> remBox,
    "e" -> addEdgeAction
  )
)

type M[T] = Action[PosGraph, T]
val L = actionLiftIO[PosGraph]
val A = implicitly[Monad[[X] =>> Action[LabeledGraph[Complex],X]]]

extension(b: PosGraph)
  def labeledVertices() = {
    val vs = b.vertices().toList
    vs.map(v => (v, b.subpart(Label[Complex], v).get))
  }

def renderPosGraph(
  $posGraph: Var[LabeledGraph[Complex]],
  hover: HoverController,
  drag: DragController
) = {

  val spriteMaps = SpriteMaps[PosGraph](
    $posGraph.signal,
    List(
      SpriteMaker[PosGraph](
        Box(),
        (s, _) => s.parts(V).toList.map(v => (v, PropMap() + (Center, s.subpart(Label[Complex], v).get))),
        Stack(
          WithDefaults(PropMap() + (MinimumWidth, 50) + (MinimumHeight, 50) + (Fill, "white") + (Stroke, "black")),
          Hoverable(hover, MainHandle, PropMap() + (Fill, "lightgray")),
          Draggable.dragPart(drag, $posGraph, Label[Complex], MainHandle)
        )
      ),
      SpriteMaker[PosGraph](
        Arrow(),
        (s, propMap) => s.parts(E).toList.map(
          e => {
            val srcEnt = s.subpart(Src, e).get
            val tgtEnt = s.subpart(Tgt, e).get
            val srcCenter = propMap(srcEnt)(Center)
            val tgtCenter = propMap(tgtEnt)(Center)
            val dir = tgtCenter - srcCenter
            val src = Box().boundaryPt(srcEnt, propMap(srcEnt), dir)
            val tgt = Box().boundaryPt(tgtEnt, propMap(tgtEnt), -dir)
            (e, PropMap() + (Start, src) + (End, tgt))
          }
        ),
        Stack(
          WithDefaults(PropMap() + (Stroke, "black")),
          Shorten(5)
        )
      )
    )
  )

  svg.g(
    spriteMaps.attach
  )
}

object Main {
  def main(args: Array[String]): Unit = {
    val action: M[Unit] = for {
      $model <- ReaderT.ask.map(_.$model)
      hover <- ReaderT.ask.map(_.hover)
      drag <- ReaderT.ask.map(_.drag)
      _ <- addChild(renderPosGraph($model, hover, drag))
      _ <- bindings.runForever
    } yield ()

    mountWithAction("app-container", LabeledGraph[Complex](), action)
  }
}
