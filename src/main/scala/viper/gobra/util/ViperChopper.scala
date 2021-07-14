// Any copyright is dedicated to the Public Domain.
// http://creativecommons.org/publicdomain/zero/1.0

package viper.gobra.util
import viper.silver.{ast => vpr}

object ViperChopper {
  /** chops 'choppee' into independent Viper programs */
  def chop(choppee: vpr.Program): Vector[vpr.Program] = {

    val edges = choppee.members.flatMap(Edges.dependencies)
    val (components, _, componentDAG) = SCC.compute(edges)
    val roots = Roots.roots(components, componentDAG)
    // TODO: currently, the always vertex is ignored
    val paths = roots.flatMap(root => Paths.paths(root, componentDAG))
    val programs = paths.map(path => path.flatMap(_.nodes).distinct)
    val verticesToProgram = Vertex.inverse(choppee)
    programs.map(verticesToProgram)
  }

  object SCC {

    case class Component[T](nodes: Seq[T])

    def components[T](graph: Seq[Edge[T]]): Vector[Component[T]] = {
      // TODO: implement SCC algorithm
      ???
    }

    def compute[T](graph: Seq[Edge[T]]): (Vector[Component[T]], Map[T, Component[T]], Seq[Edge[Component[T]]]) = {
      // TODO: merge into one function together with `component`, if this can be done quicker
      val cs = components(graph)
      val inv = cs.flatMap(c => c.nodes.map(_ -> c)).toMap
      val cgraph = graph.map{ case (l,r) => (inv(l), inv(r)) }.filter{ case (l,r) => l != r }.distinct
      (cs, inv, cgraph)
    }

  }

  object Roots {
    /** Returns all roots of a forest */
    def roots[T](nodes: Vector[T], forest: Seq[Edge[T]]): Vector[T] = {
      nodes.filter(n => !forest.exists(_._2 == n))
    }
  }

  object Paths {
    /** Returns all paths starting from `start` in the forest `forest`. */
    def paths[T](start: T, forest: Seq[Edge[T]]): Vector[Vector[T]] = {
      // TODO:
      ???
    }
  }


  sealed trait Vertex
  object Vertex {
    case class Method(methodName: String) extends Vertex
    case class MethodSpec(methodName: String) extends Vertex
    case class Function(functionName: String) extends Vertex
    case class Predicate(predicateName: String) extends Vertex
    case class Field(fieldName: String) extends Vertex
    case class DomainFunction(funcName: String) extends Vertex
    case class DomainAxiom(v: vpr.DomainAxiom, d: vpr.Domain) extends Vertex
    case class DomainType(v: vpr.DomainType) extends Vertex
    case object Always extends Vertex // if something always has to be included

    /** Returns the subprogram induces by the set of vertices. */
    def inverse(program: vpr.Program): Vector[Vertex] => vpr.Program = {
      val methodTable = program.methods.map(n => (n.name, n)).toMap
      val functionTable = program.functions.map(n => (n.name, n)).toMap
      val predicateTable = program.predicates.map(n => (n.name, n)).toMap
      val fieldTable = program.fields.map(n => (n.name, n)).toMap
      val domainTable = program.domains.map(n => (n.name, n)).toMap
      val domainFunctionTable = program.domains.flatMap(d => d.functions.map(f => (f.name, (f, d)))).toMap

      { (vertices: Vector[Vertex]) =>

        val methods = {
          val ms = vertices.collect{ case v: Method => methodTable(v.methodName) }
          val stubs = vertices.collect{ case v: MethodSpec => val m = methodTable(v.methodName); m.copy(body = None)(m.pos, m.info, m.errT) }
          val filteredStubs = stubs.filterNot(stub => ms.exists(_.name == stub.name))
          ms ++ filteredStubs
        }
        val funcs = vertices.collect{ case v: Function => functionTable(v.functionName) }
        val preds = vertices.collect{ case v: Predicate => predicateTable(v.predicateName) }
        val fields = vertices.collect{ case v: Field => fieldTable(v.fieldName) }
        val domains = {
          val fs = vertices.collect{ case v: DomainFunction => domainFunctionTable(v.funcName) }.groupMap(_._2)(_._1)
          val as = vertices.collect{ case v: DomainAxiom => (v.v, v.d)}.groupMap(_._2)(_._1)
          val ds = vertices.collect{ case v: DomainType => domainTable(v.v.domainName) }
          val totalDs = (ds ++ fs.keys ++ as.keys).distinct

          totalDs.map{ d =>
            d.copy(functions = fs(d), axioms = as(d))(d.pos, d.info, d.errT)
          }
        }

        program.copy(
          methods = methods,
          functions = funcs,
          predicates = preds,
          fields = fields,
          domains = domains
        )(program.pos, program.info, program.errT)
      }
    }
  }

  type Edge[T] = (T, T)
  object Edges {

    /** Returns all dependencies induced by a member. */
    def dependencies(member: vpr.Member): Seq[Edge[Vertex]] = {
      member match {
        case m: vpr.Method =>
          {
            val from = Vertex.Method(m.name)
            usages(m).map(from -> _)
          } ++ {
            val from = Vertex.MethodSpec(m.name)
            (m.pres ++ m.posts ++ m.formalArgs ++ m.formalReturns).flatMap(exp => usages(exp).map(from -> _))
          }

        case f: vpr.Function =>
          val from = Vertex.Function(f.name)
          usages(f).map(from -> _)

        case p: vpr.Predicate =>
          val from = Vertex.Predicate(p.name)
          usages(p).map(from -> _)

        case f: vpr.Field =>
          f.typ match {
            case t: vpr.DomainType =>
              Vector(Vertex.Field(f.name) -> Vertex.DomainType(t))
            case _ => Vector.empty
          }

        case d: vpr.Domain =>
          d.axioms.flatMap{ ax =>
            val mid = Vertex.DomainAxiom(ax, d)
            val tos = usages(ax.exp)
            val froms = tos // this is just an approximation for now. This could be improved.
            val finalFroms = if (froms.nonEmpty) froms else Vector(Vertex.Always)
            finalFroms.map(_ -> mid) ++ tos.map(mid -> _)
          } ++ d.functions.flatMap{ f =>
            val from = Vertex.DomainFunction(f.name)
            usages(f).map(from -> _)
          }

        case _ => Vector.empty
      }
    }

    /** Returns all entities referenced in the subtree of node `n`. */
    def usages(n: vpr.Node): Seq[Vertex] = {

      def unit(n: vpr.Node): Seq[Vertex] = {
        n match {
          case n: vpr.Exp if n.typ.isInstanceOf[vpr.DomainType] =>
            Vector(Vertex.DomainType(n.typ.asInstanceOf[vpr.DomainType]))

          case _ => Vector.empty
        }
      }

      n.deepCollect{
        case n: vpr.MethodCall => Vertex.MethodSpec(n.methodName) +: unit(n)
        case n: vpr.FuncApp => Vertex.Function(n.funcname) +: unit(n)
        case n: vpr.DomainFuncApp => Vertex.DomainFunction(n.funcname) +: unit(n)
        case n: vpr.PredicateAccess => Vertex.Predicate(n.predicateName) +: unit(n)
        case n: vpr.FieldAccess => Vertex.Field(n.field.name) +: unit(n)
        case n: vpr.Exp => unit(n)
      }.flatten
    }
  }



}
