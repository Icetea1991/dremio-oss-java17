/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio.exec.planner.sql.parser;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlSpecialOperator;
import org.apache.calcite.sql.SqlWriter;
import org.apache.calcite.sql.parser.SqlParserPos;

import com.dremio.common.exceptions.UserException;
import com.dremio.exec.ExecConstants;
import com.dremio.exec.ops.QueryContext;
import com.dremio.exec.planner.sql.handlers.direct.SimpleDirectHandler;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * ALTER { TABLE | VIEW } [ IF EXISTS ] <table_name>
 *    ADD ROW ACCESS POLICY <function_name> ( <column_name> [, ... ] )
 */
public class SqlAlterTableAddRowAccessPolicy extends SqlAlterTable implements SimpleDirectHandler.Creator {
  private final SqlPolicy policy;

  public static final SqlSpecialOperator OPERATOR = new SqlSpecialOperator("SET_ROW_ACCESS_POLICY", SqlKind.OTHER) {
    @Override
    public SqlCall createCall(SqlLiteral functionQualifier, SqlParserPos pos, SqlNode... operands) {
      Preconditions.checkArgument(operands.length == 2, "SqlAlterTableAddRowAccessPolicy.createCall() has to get 2 operands!");
      return new SqlAlterTableAddRowAccessPolicy(
        pos,
        (SqlIdentifier) operands[0],
        (SqlPolicy) operands[1]
      );
    }
  };

  public SqlAlterTableAddRowAccessPolicy(SqlParserPos pos, SqlIdentifier tblName, SqlPolicy policy) {
    super(pos, tblName);
    this.policy = policy;
  }

  public SqlPolicy getPolicy() {
    return policy;
  }

  @Override
  public SqlOperator getOperator() {
    return OPERATOR;
  }

  @Override
  public List<SqlNode> getOperandList() {
    return ImmutableList.of(
      tblName,
      policy);
  }

  @Override
  public void unparse(SqlWriter writer, int leftPrec, int rightPrec) {
    super.unparse(writer, leftPrec, rightPrec);
    writer.keyword("ADD");
    writer.keyword("ROW");
    writer.keyword("ACCESS");
    writer.keyword("POLICY");
    policy.unparse(writer, leftPrec, rightPrec);
  }

  @Override
  public SimpleDirectHandler toDirectHandler(QueryContext context) {
    if (!context.getOptions().getOption(ExecConstants.ENABLE_NATIVE_ROW_COLUMN_POLICIES)) {
      throw new UnsupportedOperationException("Native row/column policies are not supported.");
    }

    try {
      final Class<?> cl = Class.forName("com.dremio.exec.planner.sql.handlers.EnterpriseAlterTableAddRowAccessPolicy");
      final Constructor<?> ctor = cl.getConstructor(QueryContext.class);
      return (SimpleDirectHandler) ctor.newInstance(context);
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoSuchMethodException | InvocationTargetException e) {
      final UserException.Builder exceptionBuilder = UserException.unsupportedError()
        .message("This command is not supported in this edition of Dremio.");
      throw exceptionBuilder.buildSilently();
    }
  }
}
