package com.segment.segmentmetricservice.utils;

import com.querydsl.sql.codegen.MetaDataExporter;
import com.querydsl.sql.codegen.MetadataExporterConfig;
import com.querydsl.sql.codegen.MetadataExporterConfigImpl;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SqlQClassGenerator {

    public static void main(String[] args) throws Exception{
        String url = System.getProperty("codegen.url");
        String user = System.getProperty("codegen.user");
        String pwd = System.getProperty("codegen.pwd");

        Map<String, List<String>> domainTableMap = new LinkedHashMap<>();
        domainTableMap.put("user", List.of("account"));
        //domainTableMap.put("segment", List.of("segment"));
        //domainTableMap.put("metric", List.of("segment_daily_metric"));

        try (Connection conn = DriverManager.getConnection(url, user, pwd)) {
            for (Map.Entry<String, List<String>> entry : domainTableMap.entrySet()) {
                String domainName = entry.getKey();
                List<String> tableNames = entry.getValue();

                for (String tableName : tableNames){
                    String packageName = "com.segment.segmentmetricservice.domain."+domainName+".sql";
                    MetadataExporterConfig config = new GradleConfig(packageName, tableName);
                    MetaDataExporter exporter = new MetaDataExporter(config);
                    exporter.export(conn.getMetaData());
                }
            }
            System.out.println("=== QueryDSL-SQL S클래스 생성 완료 ===");
        } catch (Exception e) {
            System.err.println("S클래스를 생성하는 중 문제가 발생했습니다.");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static class GradleConfig extends MetadataExporterConfigImpl {
        private final String packageName;
        private final File targetFolder = new File("src/main/generated");
        private final String namePrefix = "S";
        private final String tableNamePattern;

        private GradleConfig(String packageName, String tableNamePattern) {
            super();
            this.packageName = packageName;
            this.tableNamePattern = tableNamePattern;
        }

        @Override public String getPackageName() { return this.packageName; }
        @Override public File getTargetFolder() { return this.targetFolder; }
        @Override public String getNamePrefix() { return this.namePrefix; }
        @Override public String getTableNamePattern() { return this.tableNamePattern; }

    }
}
