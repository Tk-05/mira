package com.mira.runtime.visitors;

import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.VarDecl;

public interface StmtVisitor<T> {

    public T visitVarDecl(VarDecl stmt);

    public T visitFuncDecl(FuncDecl stmt);

    public T visitReturn(Return stmt);

    public T visitAssign(Assign stmt);

    public T visitIf(If stmt);
}
