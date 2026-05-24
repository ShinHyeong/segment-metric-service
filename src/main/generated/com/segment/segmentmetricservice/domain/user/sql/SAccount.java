package com.segment.segmentmetricservice.domain.user.sql;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.dsl.StringTemplate;

import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.annotations.Generated;
import com.querydsl.core.types.Path;

import com.querydsl.sql.ColumnMetadata;
import java.sql.Types;




/**
 * SAccount is a Querydsl query type for SAccount
 */
@SuppressWarnings("this-escape")
@Generated("com.querydsl.sql.codegen.MetaDataSerializer")
public class SAccount extends com.querydsl.sql.RelationalPathBase<SAccount> {

    private static final long serialVersionUID = -1661784921;

    public static final SAccount account = new SAccount("account");

    public final NumberPath<Integer> age = createNumber("age", Integer.class);

    public final StringPath gender = createString("gender");

    public final StringPath location = createString("location");

    public final StringPath name = createString("name");

    public final NumberPath<Integer> orderCount = createNumber("orderCount", Integer.class);

    public final NumberPath<Long> userId = createNumber("userId", Long.class);

    public final com.querydsl.sql.PrimaryKey<SAccount> primary = createPrimaryKey(userId);

    public SAccount(String variable) {
        super(SAccount.class, forVariable(variable), "null", "account");
        addMetadata();
    }

    public SAccount(String variable, String schema, String table) {
        super(SAccount.class, forVariable(variable), schema, table);
        addMetadata();
    }

    public SAccount(String variable, String schema) {
        super(SAccount.class, forVariable(variable), schema, "account");
        addMetadata();
    }

    public SAccount(Path<? extends SAccount> path) {
        super(path.getType(), path.getMetadata(), "null", "account");
        addMetadata();
    }

    public SAccount(PathMetadata metadata) {
        super(SAccount.class, metadata, "null", "account");
        addMetadata();
    }

    public void addMetadata() {
        addMetadata(age, ColumnMetadata.named("age").withIndex(4).ofType(Types.INTEGER).withSize(10).notNull());
        addMetadata(gender, ColumnMetadata.named("gender").withIndex(3).ofType(Types.VARCHAR).withSize(10).notNull());
        addMetadata(location, ColumnMetadata.named("location").withIndex(5).ofType(Types.VARCHAR).withSize(100).notNull());
        addMetadata(name, ColumnMetadata.named("name").withIndex(2).ofType(Types.VARCHAR).withSize(50).notNull());
        addMetadata(orderCount, ColumnMetadata.named("order_count").withIndex(6).ofType(Types.INTEGER).withSize(10).notNull());
        addMetadata(userId, ColumnMetadata.named("user_id").withIndex(1).ofType(Types.BIGINT).withSize(19).notNull());
    }

}

