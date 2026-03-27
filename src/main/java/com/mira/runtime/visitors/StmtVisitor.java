package com.mira.runtime.visitors;

import com.mira.parser.nodes.statement.Statement.Assign;
import com.mira.parser.nodes.statement.Statement.Block;
import com.mira.parser.nodes.statement.Statement.Break;
import com.mira.parser.nodes.statement.Statement.For;
import com.mira.parser.nodes.statement.Statement.Foreach;
import com.mira.parser.nodes.statement.Statement.FuncDecl;
import com.mira.parser.nodes.statement.Statement.If;
import com.mira.parser.nodes.statement.Statement.Overwrite;
import com.mira.parser.nodes.statement.Statement.Return;
import com.mira.parser.nodes.statement.Statement.VarDecl;
import com.mira.parser.nodes.statement.Statement.While;

public interface StmtVisitor<T> {

    public T visitVarDecl(VarDecl stmt);

    public T visitFuncDecl(FuncDecl stmt);

    public T visitReturn(Return stmt);

    public T visitAssign(Assign stmt);

    public T visitIf(If stmt);

    public T visitFor(For stmt);

    public T visitWhile(While stmt);

    public T visitBreak(Break stmt);

    public T visitBlock(Block stmt);

    public T visitOverwrite(Overwrite stmt);

    public T visitForeach(Foreach stmt);
}
