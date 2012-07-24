/*
 [The "BSD license"]
  Copyright (c) 2011 Terence Parr
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions
  are met:

  1. Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.
  2. Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following disclaimer in the
     documentation and/or other materials provided with the distribution.
  3. The name of the author may not be used to endorse or promote products
     derived from this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.antlr.v4.runtime.atn;

import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.misc.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;

/** An ATN state, predicted alt, and syntactic/semantic context.
 *  The syntactic context is a pointer into the rule invocation
 *  chain used to arrive at the state.  The semantic context is
 *  the unordered set semantic predicates encountered before reaching
 *  an ATN state.
 *
 *  (state, alt, rule context, semantic context)
 */
public class ATNConfig {
	/** The ATN state associated with this configuration */
	@NotNull
	private final ATNState state;

	/** What alt (or lexer rule) is predicted by this configuration */
	private final int alt;

	/** The stack of invoking states leading to the rule/states associated
	 *  with this config.  We track only those contexts pushed during
	 *  execution of the ATN simulator.
	 */
	@Nullable
	private PredictionContext context;

	private int reachesIntoOuterContext;

	/** Capture lexer action we traverse */
	private final int lexerActionIndex; // TOOD: move to subclass

    @NotNull
    private final SemanticContext semanticContext;

	private ATNConfig(@NotNull ATNState state,
					 int alt,
					 @Nullable PredictionContext context,
					 @NotNull SemanticContext semanticContext,
					 int actionIndex)
	{
		this.state = state;
		this.alt = alt;
		this.context = context;
		this.semanticContext = semanticContext;
		this.lexerActionIndex = actionIndex;
	}

	private ATNConfig(@NotNull ATNConfig c, @NotNull ATNState state, @Nullable PredictionContext context,
                     @NotNull SemanticContext semanticContext, int actionIndex)
    {
		this.state = state;
		this.alt = c.alt;
		this.context = context;
		this.reachesIntoOuterContext = c.reachesIntoOuterContext;
        this.semanticContext = semanticContext;
		this.lexerActionIndex = actionIndex;
	}

	public static ATNConfig create(@NotNull ATNState state, int alt, @Nullable PredictionContext context) {
		return create(state, alt, context, SemanticContext.NONE, -1);
	}

	public static ATNConfig create(@NotNull ATNState state, int alt, @Nullable PredictionContext context, @NotNull SemanticContext semanticContext) {
		return create(state, alt, context, semanticContext, -1);
	}

	public static ATNConfig create(@NotNull ATNState state, int alt, @Nullable PredictionContext context, @NotNull SemanticContext semanticContext, int actionIndex) {
		return new ATNConfig(state, alt, context, semanticContext, actionIndex);
	}

	/** Gets the ATN state associated with this configuration */
	@NotNull
	public final ATNState getState() {
		return state;
	}

	/** What alt (or lexer rule) is predicted by this configuration */
	public final int getAlt() {
		return alt;
	}

	@Nullable
	public final PredictionContext getContext() {
		return context;
	}

	public final void setContext(@NotNull PredictionContext context) {
		this.context = context;
	}

	public final boolean getReachesIntoOuterContext() {
		return getOuterContextDepth() != 0;
	}

	/**
	 * We cannot execute predicates dependent upon local context unless
	 * we know for sure we are in the correct context. Because there is
	 * no way to do this efficiently, we simply cannot evaluate
	 * dependent predicates unless we are in the rule that initially
	 * invokes the ATN simulator.
	 *
	 * closure() tracks the depth of how far we dip into the
	 * outer context: depth > 0.  Note that it may not be totally
	 * accurate depth since I don't ever decrement. TODO: make it a boolean then
	 */
	public final int getOuterContextDepth() {
		return reachesIntoOuterContext;
	}

	public final void setOuterContextDepth(int outerContextDepth) {
		this.reachesIntoOuterContext = outerContextDepth;
	}

	public final int getActionIndex() {
		return lexerActionIndex;
	}

	@NotNull
	public final SemanticContext getSemanticContext() {
		return semanticContext;
	}

	@Override
	public final ATNConfig clone() {
		return transform(this.getState());
	}

	public final ATNConfig transform(@NotNull ATNState state) {
		return transform(state, this.context, this.getSemanticContext(), this.getActionIndex());
	}

	public final ATNConfig transform(@NotNull ATNState state, @NotNull SemanticContext semanticContext) {
		return transform(state, this.context, semanticContext, this.getActionIndex());
	}

	public final ATNConfig transform(@NotNull ATNState state, @Nullable PredictionContext context) {
		return transform(state, context, this.getSemanticContext(), this.getActionIndex());
	}

	public final ATNConfig transform(@NotNull ATNState state, int actionIndex) {
		return transform(state, context, this.getSemanticContext(), actionIndex);
	}

	private ATNConfig transform(@NotNull ATNState state, @Nullable PredictionContext context, @NotNull SemanticContext semanticContext, int actionIndex) {
		return new ATNConfig(this, state, context, semanticContext, actionIndex);
	}

	public ATNConfig appendContext(int context, PredictionContextCache contextCache) {
		PredictionContext appendedContext = getContext().appendContext(context, contextCache);
		ATNConfig result = transform(getState(), appendedContext);
		return result;
	}

	public ATNConfig appendContext(PredictionContext context, PredictionContextCache contextCache) {
		PredictionContext appendedContext = getContext().appendContext(context, contextCache);
		ATNConfig result = transform(getState(), appendedContext);
		return result;
	}

	public boolean contains(ATNConfig subconfig) {
		if (this.getState().stateNumber != subconfig.getState().stateNumber
			|| this.getAlt() != subconfig.getAlt()
			|| !this.getSemanticContext().equals(subconfig.getSemanticContext())) {
			return false;
		}

		Deque<PredictionContext> leftWorkList = new ArrayDeque<PredictionContext>();
		Deque<PredictionContext> rightWorkList = new ArrayDeque<PredictionContext>();
		leftWorkList.add(getContext());
		rightWorkList.add(subconfig.getContext());
		while (!leftWorkList.isEmpty()) {
			PredictionContext left = leftWorkList.pop();
			PredictionContext right = rightWorkList.pop();

			if (left == right) {
				return true;
			}

			if (left.size() < right.size()) {
				return false;
			}

			if (right.isEmpty()) {
				return left.hasEmpty();
			} else {
				for (int i = 0; i < right.size(); i++) {
					int index = left.findInvokingState(right.getInvokingState(i));
					if (index < 0) {
						// assumes invokingStates has no duplicate entries
						return false;
					}

					leftWorkList.push(left.getParent(index));
					rightWorkList.push(right.getParent(i));
				}
			}
		}

		return false;
	}

	/** An ATN configuration is equal to another if both have
     *  the same state, they predict the same alternative, and
     *  syntactic/semantic contexts are the same.
     */
    @Override
    public boolean equals(Object o) {
		if (!(o instanceof ATNConfig)) {
			return false;
		}

		return this.equals((ATNConfig)o);
	}

	public boolean equals(ATNConfig other) {
		if (this == other) {
			return true;
		} else if (other == null) {
			return false;
		}

		return this.getState().stateNumber==other.getState().stateNumber
			&& this.getAlt()==other.getAlt()
			&& this.getReachesIntoOuterContext() == other.getReachesIntoOuterContext()
			&& (this.getContext()==other.getContext() || (this.getContext() != null && this.getContext().equals(other.getContext())))
			&& this.getSemanticContext().equals(other.getSemanticContext())
			&& this.getActionIndex() == other.getActionIndex();
	}

	@Override
	public int hashCode() {
		int hashCode = 7;
		hashCode = 5 * hashCode + getState().stateNumber;
		hashCode = 5 * hashCode + getAlt();
		hashCode = 5 * hashCode + (getReachesIntoOuterContext() ? 1 : 0);
		hashCode = 5 * hashCode + (getContext() != null ? getContext().hashCode() : 0);
		hashCode = 5 * hashCode + getSemanticContext().hashCode();
        return hashCode;
    }

	public String toDotString() {
		StringBuilder builder = new StringBuilder();
		builder.append("digraph G {\n");
		builder.append("rankdir=LR;\n");

		Map<PredictionContext, PredictionContext> visited = new IdentityHashMap<PredictionContext, PredictionContext>();
		Deque<PredictionContext> workList = new ArrayDeque<PredictionContext>();
		workList.add(getContext());
		visited.put(getContext(), getContext());
		while (!workList.isEmpty()) {
			PredictionContext current = workList.pop();
			for (int i = 0; i < current.size(); i++) {
				builder.append("  s").append(System.identityHashCode(current));
				builder.append("->");
				builder.append("s").append(System.identityHashCode(current.getParent(i)));
				builder.append("[label=\"").append(current.getInvokingState(i)).append("\"];\n");
				if (visited.put(current.getParent(i), current.getParent(i)) == null) {
					workList.push(current.getParent(i));
				}
			}
		}

		builder.append("}\n");
		return builder.toString();
	}

	@Override
	public String toString() {
		return toString(null, true, false);
	}

	public String toString(@Nullable Recognizer<?, ?> recog, boolean showAlt) {
		return toString(recog, showAlt, true);
	}

	public String toString(@Nullable Recognizer<?, ?> recog, boolean showAlt, boolean showContext) {
		StringBuilder buf = new StringBuilder();
//		if ( state.ruleIndex>=0 ) {
//			if ( recog!=null ) buf.append(recog.getRuleNames()[state.ruleIndex]+":");
//			else buf.append(state.ruleIndex+":");
//		}
		String[] contexts;
		if (showContext) {
			contexts = getContext().toStrings(recog, this.getState().stateNumber);
		}
		else {
			contexts = new String[] { "?" };
		}
		boolean first = true;
		for (String contextDesc : contexts) {
			if ( first ) {
				first = false;
			}
			else {
				buf.append(", ");
			}

			buf.append('(');
			buf.append(getState());
			if ( showAlt ) {
				buf.append(",");
				buf.append(getAlt());
			}
			if ( getContext()!=null ) {
				buf.append(",");
				buf.append(contextDesc);
			}
			if ( getSemanticContext()!=null && getSemanticContext() != SemanticContext.NONE ) {
				buf.append(",");
				buf.append(getSemanticContext());
			}
			if ( getReachesIntoOuterContext() ) {
				buf.append(",up=").append(getOuterContextDepth());
			}
			buf.append(')');
		}
		return buf.toString();
    }
}
