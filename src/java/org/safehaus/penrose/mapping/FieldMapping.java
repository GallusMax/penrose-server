/**
 * Copyright (c) 2000-2005, Identyx Corporation.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.safehaus.penrose.mapping;

/**
 * @author Endi S. Dewata
 */
public class FieldMapping implements Cloneable {

	/**
	 * Name.
	 */
	private String name;

    /**
     * Script.
     */
    private String script;

	/**
	 * Expression.
	 */
    private String constant;
    private String variable;
	private Expression expression;


    public FieldMapping() {
    }
    
    public FieldMapping(String name) {
        this.name = name;
    }

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Expression getExpression() {
		return expression;
	}

	public void setExpression(Expression expression) {
		this.expression = expression;
	}

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public int hashCode() {
        return (name == null ? 0 : name.hashCode()) +
                (script == null ? 0 : script.hashCode()) +
                (constant == null ? 0 : constant.hashCode()) +
                (variable == null ? 0 : variable.hashCode()) +
                (expression == null ? 0 : expression.hashCode());
    }

    boolean equals(Object o1, Object o2) {
        if (o1 == null && o2 == null) return true;
        if (o1 != null) return o1.equals(o2);
        return o2.equals(o1);
    }

    public boolean equals(Object object) {
        if (this == object) return true;
        if((object == null) || (object.getClass() != this.getClass())) return false;

        FieldMapping fieldMapping = (FieldMapping)object;
        if (!equals(name, fieldMapping.name)) return false;
        if (!equals(script, fieldMapping.script)) return false;
        if (!equals(constant, fieldMapping.constant)) return false;
        if (!equals(variable, fieldMapping.variable)) return false;
        if (!equals(expression, fieldMapping.expression)) return false;

        return true;
    }

    public Object clone() {
        FieldMapping fieldMapping = new FieldMapping();
        fieldMapping.name = name;
        fieldMapping.script = script;
        fieldMapping.constant = constant;
        fieldMapping.variable = variable;
        fieldMapping.expression = expression == null ? null : (Expression)expression.clone();
        return fieldMapping;
    }

    public String toString() {
        return name;
    }

    public String getConstant() {
        return constant;
    }

    public void setConstant(String constant) {
        this.constant = constant;
    }

    public String getVariable() {
        return variable;
    }

    public void setVariable(String variable) {
        this.variable = variable;
    }
}