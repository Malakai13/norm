package com.dieselpoint.norm.sqlmakers;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import com.dieselpoint.norm.DbException;
import com.dieselpoint.norm.Query;
import com.dieselpoint.norm.Util;
import com.dieselpoint.norm.sqlmakers.StandardPojoInfo.Property;

/**
 * Produces ANSI-standard SQL. Extend this class to handle different flavors of sql.
 */
public class StandardSqlMaker implements SqlMaker {

	private static ConcurrentHashMap<Class, StandardPojoInfo> map = new ConcurrentHashMap<Class, StandardPojoInfo>();

	public StandardPojoInfo getPojoInfo(Class rowClass) {
		StandardPojoInfo pi = map.get(rowClass);
		if (pi == null) {
			pi = new StandardPojoInfo(rowClass);
			map.put(rowClass, pi);
			
			makeInsertSql(pi);
			makeUpdateSql(pi);
			makeSelectColumns(pi);
		}
		return pi;
	}
	
	
	@Override
	public String getInsertSql(Query query, Object row) {
		StandardPojoInfo pojoInfo = getPojoInfo(row.getClass());
		return pojoInfo.insertSql;
	}
	
	@Override
	public Object[] getInsertArgs(Query query, Object row) {
		StandardPojoInfo pojoInfo = getPojoInfo(row.getClass());
		Object [] args = new Object[pojoInfo.insertSqlArgCount];
		for (int i = 0; i < pojoInfo.insertSqlArgCount; i++) {
			args[i] = pojoInfo.getValue(row, pojoInfo.insertColumnNames[i]);
		}
		return args;
	}
	
	@Override
	public String getUpdateSql(Query query, Object row) {
		StandardPojoInfo pojoInfo = getPojoInfo(row.getClass());
		if (pojoInfo.primaryKeyName == null) {
			throw new DbException("No primary key specified in the row. Use the @Id annotation.");
		}
		return pojoInfo.updateSql;
	}

	@Override
	public Object[] getUpdateArgs(Query query, Object row) {
		StandardPojoInfo pojoInfo = getPojoInfo(row.getClass());
		
		Object [] args = new Object[pojoInfo.updateSqlArgCount];
		for (int i = 0; i < pojoInfo.updateSqlArgCount - 1; i++) {
			args[i] = pojoInfo.getValue(row, pojoInfo.updateColumnNames[i]);
		}
		// add the value for the where clause to the end
		Object pk = pojoInfo.getValue(row, pojoInfo.primaryKeyName);
		args[pojoInfo.updateSqlArgCount - 1] = pk;
		return args;
	}




	public void makeUpdateSql(StandardPojoInfo pojoInfo) {
		
		ArrayList<String> cols = new ArrayList<String>();
		for (Property prop: pojoInfo.propertyMap.values()) {
			
			if (prop.isPrimaryKey) {
				continue;
			}
			
			if (prop.isGenerated) {
				continue;
			}
			
			cols.add(prop.name);
		}
		pojoInfo.updateColumnNames = cols.toArray(new String [cols.size()]);
		pojoInfo.updateSqlArgCount = pojoInfo.updateColumnNames.length + 1; // + 1 for the where arg
		
		StringBuilder buf = new StringBuilder();
		buf.append("update ");
		buf.append(pojoInfo.table);
		buf.append(" set ");

		for (int i = 0; i < cols.size(); i++) {
			if (i > 0) {
				buf.append(',');
			}
			buf.append(cols.get(i) + "=?");
		}
		buf.append(" where " + pojoInfo.primaryKeyName + "=?");
		
		pojoInfo.updateSql = buf.toString();
	}
	

	
	public void makeInsertSql(StandardPojoInfo pojoInfo) {
		ArrayList<String> cols = new ArrayList<String>();
		for (Property prop: pojoInfo.propertyMap.values()) {
			if (prop.isGenerated) {
				continue;
			}
			cols.add(prop.name);
		}
		pojoInfo.insertColumnNames = cols.toArray(new String [cols.size()]);
		pojoInfo.insertSqlArgCount = pojoInfo.insertColumnNames.length;
		
		StringBuilder buf = new StringBuilder();
		buf.append("insert into ");
		buf.append(pojoInfo.table);
		buf.append(" (");
		buf.append(Util.join(pojoInfo.insertColumnNames)); // comma sep list?
		buf.append(") values (");
		buf.append(Util.getQuestionMarks(pojoInfo.insertSqlArgCount));
		buf.append(")");
		
		pojoInfo.insertSql = buf.toString();
	}


	private void makeSelectColumns(StandardPojoInfo pojoInfo) {
		ArrayList<String> cols = new ArrayList<String>();
		for (Property prop: pojoInfo.propertyMap.values()) {
			cols.add(prop.name);
		}
		pojoInfo.selectColumns = Util.join(cols);
	}


	@Override
	public String getSelectSql(Query query, Class rowClass) {

		// unlike insert and update, this needs to be done dynamically
		// and can't be precalculated because of the where and order by
		
		StandardPojoInfo pojoInfo = getPojoInfo(rowClass);
		String columns = pojoInfo.selectColumns;
		
		String where = query.getWhere();
		String table = query.getTable();
		if (table == null) {
			table = pojoInfo.table;
		}
		String orderBy = query.getOrderBy();
		
		StringBuilder out = new StringBuilder();
		out.append("select ");
		out.append(columns);
		out.append(" from ");
		out.append(table);
		if (where != null) {
			out.append(" where ");
			out.append(where);
		}
		if (orderBy != null) {
			out.append(" order by ");
			out.append(orderBy);
		}
		return out.toString();
	}


	/*
	@Override
	public void putValue(Object pojo, String name, Object value) {
		StandardPojoInfo pojoInfo = getPojoInfo(pojo.getClass());
		try {
			pojoInfo.putValue(pojo, name, value);
		} catch (NoSuchFieldException | IllegalAccessException
				| IllegalArgumentException | InvocationTargetException e) {
			throw new DbException(e);
		}
	}



	*/


	@Override
	public String getCreateTableSql(Class<?> clazz) {
		
		StringBuilder buf = new StringBuilder();

		StandardPojoInfo pojoInfo = getPojoInfo(clazz);
		buf.append("create table ");
		buf.append(pojoInfo.table);
		buf.append(" (");
		
		boolean needsComma = false;
		for (Property prop : pojoInfo.propertyMap.values()) {
			
			if (needsComma) {
				buf.append(',');
			}
			needsComma = true;

			// TODO use @ Column properties
			String colType = "varchar(512)";
			Class<?> dataType = prop.dataType;
			
			if (dataType.equals(Integer.class) || dataType.equals(int.class)) {
				colType = "integer";
			} else if (dataType.equals(Long.class) || dataType.equals(long.class)) {
				colType = "bigint";
			} else if (dataType.equals(Double.class) || dataType.equals(double.class)) {
				colType = "double";
			} else if (dataType.equals(Float.class) || dataType.equals(float.class)) {
				colType = "float";
			}
			
			buf.append(prop.name);
			buf.append(" ");
			buf.append(colType);
			
			if (prop.isGenerated) {
				buf.append(" auto_increment");
			}
			
		}
		
		if (pojoInfo.primaryKeyName != null) {
			buf.append(", primary key (");
			buf.append(pojoInfo.primaryKeyName);
			buf.append(")");
		}
		
		buf.append(")");
		
		return buf.toString();
	}


	@Override
	public String getDeleteSql(Query query, Object row) {
		
		StandardPojoInfo pojoInfo = getPojoInfo(row.getClass());
		
		String table = query.getTable();  
		if (table == null) {
			table = pojoInfo.table;
			if (table == null) {
				throw new DbException("You must specify a table name");
			}
		}
		
		String primaryKeyName = pojoInfo.primaryKeyName;
		
		return "delete from " + table + " where " + primaryKeyName + "=?";
	}


	@Override
	public Object[] getDeleteArgs(Query query, Object row) {
		StandardPojoInfo pojoInfo = getPojoInfo(row.getClass());
		Object primaryKeyValue = pojoInfo.getValue(row, pojoInfo.primaryKeyName);
		Object [] args = new Object[1];
		args[0] = primaryKeyValue;
		return args;
	}


	@Override
	public String getUpsertSql(Query query, Object row) {
		String msg =
				"There's no standard upsert implemention. There is one in the MySql driver, though,"
				+ "so if you're using MySql, call Database.setSqlMaker(new MySqlMaker()); Or roll your own.";
		throw new UnsupportedOperationException(msg);
	}


	@Override
	public Object[] getUpsertArgs(Query query, Object row) {
		throw new UnsupportedOperationException();
	}


}