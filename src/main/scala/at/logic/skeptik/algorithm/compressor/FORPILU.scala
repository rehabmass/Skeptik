package at.logic.skeptik.algorithm.compressor

import at.logic.skeptik.proof.Proof
import at.logic.skeptik.proof.sequent._
import at.logic.skeptik.proof.sequent.lk._
import at.logic.skeptik.judgment.immutable.{ SeqSequent => Sequent }
import at.logic.skeptik.judgment.immutable.{ SetSequent => IClause }
import at.logic.skeptik.expression._
import collection.mutable.{ HashMap => MMap, HashSet => MSet }
import collection.Map
import at.logic.skeptik.proof.sequent.resolution.UnifyingResolution
import at.logic.skeptik.proof.sequent.resolution.UnifyingResolutionMRR
import at.logic.skeptik.proof.sequent.resolution.Contraction
import at.logic.skeptik.proof.sequent.resolution.CanRenameVariables
import at.logic.skeptik.proof.sequent.resolution.FindDesiredSequent
import at.logic.skeptik.algorithm.unifier.{ MartelliMontanari => unify }

abstract class FOAbstractRPILUAlgorithm
  extends AbstractRPILUAlgorithm with FindDesiredSequent with CanRenameVariables {

  protected def checkForRes(safeLiteralsHalf: Set[E], aux: E, unifiableVars: MSet[Var]): Boolean = {

    if (safeLiteralsHalf.size < 1) {
      return false
    }

    /* 
     * unifiableVars might not contain the variables in the aux formulae. When UR(MRR) generates the auxL/auxR formulae,
     * it may rename the variables in one premise to a new premise that we just haven't seen yet (and which is resolved out
     * in that resolution and thus never really visible in the proof, so we need to check for new variables and
     * add them to our list of unifiable variables or the unification might fail.
     */

    /*  For example,
     * 
     *  p(X) |- q(a)     with    q(X) |- 
     * 
     *  UR might rename the right X as Y, then resolve out to get P(X) |-
     *  And while UR used q(Y) |- and recorded the aux formula as such, it didn't rename
     *  the right premise, so we never see the variable Y, even though it can be unified.
     */

    for (safeLit <- safeLiteralsHalf) {
      unify((aux, safeLit) :: Nil)(getSetOfVars(aux)) match {
        case Some(_) => {
          return true
        }
        case None => {
          //Do nothing
        }
      }
    }
    false
  }

  class FOEdgesToDelete extends EdgesToDelete {

    override def nodeIsMarked(node: SequentProofNode): Boolean = {
      // may be optimized (edgesToDelete contains node is checked 3 times)
      node match {
        case _ if ((edges contains node) && edges(node)._2) => true
        case UnifyingResolution(left, right, _, _) if (isMarked(node, left) && isMarked(node, right)) =>
          deleteNode(node)
          true
        case _ => false
      }
    }

    override protected def sideOf(parent: SequentProofNode, child: SequentProofNode) = child match {
      case UnifyingResolution(left, right, _, _) => if (parent == left) LeftDS
      else if (parent == right) RightDS
      else throw new Exception("Unable to find parent in child")
      case _ => throw new Exception("This function should never be called with child not being a UR")
    }

  }

  // Main functions
  protected def fixProofNodes(edgesToDelete: EdgesToDelete, unifiableVariables: MSet[Var])(p: SequentProofNode, fixedPremises: Seq[SequentProofNode]) = {
    lazy val fixedLeft = fixedPremises.head;
    lazy val fixedRight = fixedPremises.last;
    p match {
      case Axiom(conclusion) => p

      // If we've got a proof of false, we propagate it down the proof
      case UnifyingResolution(_, _, _, _) if (fixedLeft.conclusion.ant.isEmpty) && (fixedLeft.conclusion.suc.isEmpty) => {
        fixedLeft
      }

      case UnifyingResolution(_, _, _, _) if (fixedRight.conclusion.ant.isEmpty) && (fixedRight.conclusion.suc.isEmpty) => {
        fixedRight
      }

      // Delete nodes and edges
      case UnifyingResolution(left, right, _, _) if edgesToDelete.isMarked(p, left) => {
        fixedRight
      }
      case UnifyingResolution(left, right, _, _) if edgesToDelete.isMarked(p, right) => {
        fixedLeft
      }

      // If premises haven't been changed, we keep the proof as is (memory optimization)
      case UnifyingResolution(left, right, _, _) if (left eq fixedLeft) && (right eq fixedRight) => p

      case UnifyingResolution(left, right, pivot, _) if (desiredFound(fixedLeft.conclusion, p.conclusion)(unifiableVariables)) => {
        //If we're doing this, its because the fixed parent doesn't contain the pivot, so we replace it with 
        //the fixed parent; so the pivot better be missing.
        assert(!checkForRes(fixedLeft.conclusion.toSetSequent.suc, pivot, unifiableVariables))
        fixedLeft
      }
      case UnifyingResolution(left, right, _, pivot) if (desiredFound(fixedRight.conclusion, p.conclusion)(unifiableVariables)) => {
        //If we're doing this, its because the fixed parent doesn't contain the pivot, so we replace it with 
        //the fixed parent; so the pivot better be missing.
        assert(!checkForRes(fixedLeft.conclusion.toSetSequent.ant, pivot, unifiableVariables))
        fixedRight
      }

      // Main case (rebuild a resolution)
      case UnifyingResolution(left, right, pivot, _) => {
        try {
          UnifyingResolutionMRR(fixedRight, fixedLeft)(unifiableVariables)
        } catch {
          case e: Exception => {
            UnifyingResolutionMRR(fixedLeft, fixedRight)(unifiableVariables)
          }
        }

      }

      // When the inference is not UR, nothing is done 
      case _ => {
        p
      }
    }
  }
}

abstract class FOAbstractRPIAlgorithm
  extends FOAbstractRPILUAlgorithm with CanRenameVariables {

  protected def safeLiteralsFromChild(childWithSafeLiterals: (SequentProofNode, IClause),
    parent: SequentProofNode, edgesToDelete: FOEdgesToDelete) =
    childWithSafeLiterals match {
      case (child @ UnifyingResolution(left, right, _, auxR), safeLiterals) if left == parent =>
        if (edgesToDelete.isMarked(child, right)) safeLiterals else addLiteralSmart(safeLiterals, auxR, false, left, right)
      case (child @ UnifyingResolution(left, right, auxL, _), safeLiterals) if right == parent =>
        if (edgesToDelete.isMarked(child, left)) safeLiterals else addLiteralSmart(safeLiterals, auxL, true, left, right)

      case (_, safeLiterals) => safeLiterals
      // Unchecked Inf case _ => throw new Exception("Unknown or impossible inference rule")
    }

  protected def addLiteralSmart(seq: IClause, aux: E, addToAntecedent: Boolean, left: SequentProofNode, right: SequentProofNode): IClause = {
    //Restrict MartelliMontanari to tell whether "aux" is more general (and not just unifiable) 
    // by passing only the variables of "aux" as unifiable variables.
    val uVars = new MSet[Var]() union getSetOfVars(aux)
    val seqHalf = if (addToAntecedent) {
      seq.ant
    } else {
      seq.suc
    }
    for (lit <- seqHalf) {
      unify((lit, aux) :: Nil)(uVars) match {
        case None => {}
        case Some(_) => { return seq }
      }
    }
    if (addToAntecedent) {
      aux +: seq
    } else {
      seq + aux
    }
  }

  protected def computeSafeLiterals(proof: SequentProofNode,
    childrensSafeLiterals: Seq[(SequentProofNode, IClause)],
    edgesToDelete: FOEdgesToDelete): IClause

}

trait FOCollectEdgesUsingSafeLiterals
  extends FOAbstractRPIAlgorithm {

  protected def collectEdgesToDelete(nodeCollection: Proof[SequentProofNode], unifiableVars: MSet[Var]) = {
    val edgesToDelete = new FOEdgesToDelete()

    def visit(p: SequentProofNode, childrensSafeLiterals: Seq[(SequentProofNode, IClause)]) = {
      val safeLiterals = computeSafeLiterals(p, childrensSafeLiterals, edgesToDelete)
      p match {
        case UnifyingResolution(left, right, auxL, auxR) if (checkForRes(safeLiterals.suc, auxL, unifiableVars)) => {
          edgesToDelete.markRightEdge(p)

        }
        case UnifyingResolution(left, right, auxL, auxR) if checkForRes(safeLiterals.ant, auxR, unifiableVars) => {
          edgesToDelete.markLeftEdge(p)
        }

        case _ =>
      }
      (p, safeLiterals)
    }
    nodeCollection.bottomUp(visit)
    edgesToDelete
  }
}

trait FOUnitsCollectingBeforeFixing
  extends FOAbstractRPILUAlgorithm with CanRenameVariables {

  protected def getAllVars(proof: Proof[SequentProofNode]): MSet[Var] = {
    val out = MSet[Var]()
    for (n <- proof) {
      val vars = getSetOfVars(n)
      for (v <- vars) {
        out += v
      }
    }
    out
  }
}

//TODO: do we want to contract things in the intersection to save memory?
trait FOIntersection
  extends FOAbstractRPIAlgorithm {
  protected def computeSafeLiterals(proof: SequentProofNode,
    childrensSafeLiterals: Seq[(SequentProofNode, IClause)],
    edgesToDelete: FOEdgesToDelete): IClause = {
    childrensSafeLiterals.filter { x => !edgesToDelete.isMarked(x._1, proof) } match {
      case Nil =>
        if (!childrensSafeLiterals.isEmpty) edgesToDelete.markBothEdges(proof)
        proof.conclusion.toSetSequent
      case h :: t =>
        t.foldLeft(safeLiteralsFromChild(h, proof, edgesToDelete)) { (acc, v) => acc intersect safeLiteralsFromChild(v, proof, edgesToDelete) }
    }
  }
}
