package org.nutz.dao.impl.jdbc.sqlite;

import java.util.List;

import org.nutz.dao.DB;
import org.nutz.dao.Dao;
import org.nutz.dao.Sqls;
import org.nutz.dao.entity.Entity;
import org.nutz.dao.entity.MappingField;
import org.nutz.dao.entity.PkType;
import org.nutz.dao.impl.entity.macro.SqlFieldMacro;
import org.nutz.dao.impl.jdbc.AbstractJdbcExpert;
import org.nutz.dao.jdbc.JdbcExpertConfigFile;
import org.nutz.dao.pager.Pager;
import org.nutz.dao.sql.Pojo;
import org.nutz.dao.sql.Sql;
import org.nutz.dao.util.Pojos;

/**
 * 
 * @author wendal
 */
public class SQLiteJdbcExpert extends AbstractJdbcExpert {

    public SQLiteJdbcExpert(JdbcExpertConfigFile conf) {
        super(conf);
    }

    @Override
    public String getDatabaseType() {
        return DB.SQLITE.name();
    }

    @Override
    public boolean createEntity(Dao dao, Entity<?> en) {
        StringBuilder sb = new StringBuilder("CREATE TABLE " + en.getTableName() + "(");
        if (en.getPks().size() > 1 && en.getPkType() == PkType.ID) {
            return false;
        }
        // 创建字段
        boolean mPks = en.getPks().size() > 1;
        for (MappingField mf : en.getMappingFields()) {
            if (mf.isReadonly()) {
                continue;
            }
            sb.append('\n').append(mf.getColumnNameInSql());
            // Sqlite的整数型主键,一般都是自增的,必须定义为(PRIMARY KEY
            // AUTOINCREMENT),但这样就无法定义多主键!!
            if (mf.isId() && en.getPkType() == PkType.ID) {
                sb.append(" INTEGER PRIMARY KEY AUTOINCREMENT,");
                continue;
            } else {
                sb.append(' ').append(evalFieldType(mf));
            }
            // 非主键的 @Name，应该加入唯一性约束
            if (mf.isName() && en.getPkType() != PkType.NAME) {
                sb.append(" UNIQUE NOT NULL");
            }
            // 普通字段
            else {
                if (mf.isUnsigned()) {
                    sb.append(" UNSIGNED");
                }
                if (mf.isNotNull()) {
                    sb.append(" NOT NULL");
                }
                if (mf.isPk() && !mPks) {// 复合主键需要另外定义
                    sb.append(" PRIMARY KEY");
                }
                if (mf.hasDefaultValue()) {
                    addDefaultValue(sb, mf);
                }
            }
            sb.append(',');
        }
        // 创建主键
        List<MappingField> pks = en.getPks();
        if (mPks) {
            sb.append('\n');
            sb.append("constraint pk_").append(en.getTableName()).append(" PRIMARY KEY (");
            for (MappingField pk : pks) {
                sb.append(pk.getColumnNameInSql()).append(',');
            }
            sb.setCharAt(sb.length() - 1, ')');
            sb.append("\n ");
        }

        // 结束表字段设置
        sb.setCharAt(sb.length() - 1, ')');

        // 执行创建语句
        dao.execute(Sqls.create(sb.toString()));
        
        // 创建索引
        dao.execute(createIndexs(en).toArray(new Sql[0]));
        // 创建关联表
        createRelation(dao, en);

        return true;
    }
    
    @Override
    public Pojo fetchPojoId(Entity<?> en, MappingField idField) {
    	String autoSql = "SELECT MAX($field) AS $field FROM $view";
        Pojo autoInfo = new SqlFieldMacro(idField, autoSql);
        autoInfo.setEntity(en);
        return autoInfo;
    }

    @Override
    public void formatQuery(Pojo pojo) {
        Pager pager = pojo.getContext().getPager();
        // 需要进行分页
        if (null != pager && pager.getPageNumber() > 0) {
            pojo.append(Pojos.Items.wrapf(" LIMIT %d, %d",
                    pager.getOffset(),
                    pager.getPageSize()));
        }
    }

    @Override
    public void formatQuery(Sql sql) {
        Pager pager = sql.getContext().getPager();
        // 需要进行分页
        if (null != pager && pager.getPageNumber() > 0) {
            sql.setSourceSql(sql.getSourceSql()
                    + String.format(" LIMIT %d, %d",
                    pager.getOffset(),
                    pager.getPageSize()));
        }
    }
}
