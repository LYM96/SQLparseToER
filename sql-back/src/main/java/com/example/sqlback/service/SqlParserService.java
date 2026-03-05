package com.example.sqlback.service;

import com.example.sqlback.model.TableInfo;
import com.example.sqlback.model.ColumnInfo;
import com.example.sqlback.model.ForeignKeyInfo;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.Index;
import net.sf.jsqlparser.statement.create.table.ForeignKeyIndex;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SqlParserService {

    public List<TableInfo> parseSql(String sql) {
        List<TableInfo> tables = new ArrayList<>();
        try {
            log.info("开始解析SQL...");
            String processedSql = preprocessSql(sql);
            Statements statements = CCJSqlParserUtil.parseStatements(processedSql);

            for (Statement statement : statements.getStatements()) {
                if (statement instanceof CreateTable) {
                    TableInfo tableInfo = parseCreateTable((CreateTable) statement);
                    if (tableInfo != null) {
                        tables.add(tableInfo);
                        log.info("解析成功，识别到表名: {}, 业务名: {}",
                                tableInfo.getTableName(), tableInfo.getComment());
                    }
                }
            }
            return tables;
        } catch (JSQLParserException e) {
            log.error("SQL语法错误: {}", e.getMessage());
            throw new RuntimeException("SQL语法解析失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("解析异常", e);
            throw new RuntimeException("处理SQL时发生错误: " + e.getMessage());
        }
    }

    /**
     * 预处理SQL：移除Navicat特有的物理层描述，避免干扰JSQLParser提取COMMENT
     */
    private String preprocessSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) return "";

        // 1. 移除常见的物理层选项，这些选项常导致Options列表解析偏移
        sql = sql.replaceAll("(?i)ENGINE\\s*=\\s*\\w+", "");
        sql = sql.replaceAll("(?i)AUTO_INCREMENT\\s*=\\s*\\d+", "");
        sql = sql.replaceAll("(?i)ROW_FORMAT\\s*=\\s*\\w+", "");
        sql = sql.replaceAll("(?i)CHARACTER\\s+SET\\s*=\\s*\\w+", "");
        sql = sql.replaceAll("(?i)COLLATE\\s*=\\s*\\w+", "");

        String[] lines = sql.split("\\r?\\n");
        List<String> cleanedLines = new ArrayList<>();

        for (String line : lines) {
            String upperLine = line.trim().toUpperCase();
            // 过滤掉普通 INDEX 行
            boolean isDiscardLine = (upperLine.contains("INDEX") || upperLine.contains("KEY"))
                    && !upperLine.contains("PRIMARY KEY")
                    && !upperLine.contains("FOREIGN KEY")
                    && !upperLine.startsWith("CREATE TABLE");

            if (!isDiscardLine) {
                cleanedLines.add(line);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cleanedLines.size(); i++) {
            String current = cleanedLines.get(i);
            String next = "";
            for (int j = i + 1; j < cleanedLines.size(); j++) {
                if (!cleanedLines.get(j).trim().isEmpty()) {
                    next = cleanedLines.get(j).trim();
                    break;
                }
            }

            // 修复悬挂逗号
            if (current.trim().endsWith(",") && next.startsWith(")")) {
                current = current.substring(0, current.lastIndexOf(","));
            }
            sb.append(current).append("\n");
        }

        String result = sb.toString().replaceAll(",\\s*\\)", "\n)");
        log.debug("预处理后的SQL:\n{}", result);
        return result;
    }

    private TableInfo parseCreateTable(CreateTable createTable) {
        TableInfo tableInfo = new TableInfo();

        // 1. 提取物理表名
        String rawTableName = createTable.getTable().getName();
        String cleanTableName = rawTableName.replaceAll("[`\"'\\[\\]]", "");
        tableInfo.setTableName(cleanTableName);

        List<ColumnInfo> columns = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();
        List<ForeignKeyInfo> foreignKeys = new ArrayList<>();

        // 2. 解析列信息
        if (createTable.getColumnDefinitions() != null) {
            for (ColumnDefinition col : createTable.getColumnDefinitions()) {
                ColumnInfo columnInfo = new ColumnInfo();
                String colName = col.getColumnName().replaceAll("[`\"']", "");
                columnInfo.setName(colName);
                columnInfo.setType(col.getColDataType().toString());

                List<String> specs = col.getColumnSpecs();
                String comment = colName; // 默认注释为列名
                boolean nullable = true;

                if (specs != null) {
                    List<String> upperSpecs = specs.stream().map(String::toUpperCase).collect(Collectors.toList());
                    if (upperSpecs.contains("PRIMARY") && upperSpecs.contains("KEY")) {
                        primaryKeys.add(colName);
                    }
                    if (upperSpecs.contains("NOT") && upperSpecs.contains("NULL")) {
                        nullable = false;
                    }
                    for (int i = 0; i < specs.size(); i++) {
                        if ("COMMENT".equalsIgnoreCase(specs.get(i)) && i + 1 < specs.size()) {
                            comment = specs.get(i + 1).replaceAll("^['\"]|['\"]$", "");
                            break;
                        }
                    }
                }
                columnInfo.setNullable(nullable);
                columnInfo.setComment(comment);
                columns.add(columnInfo);
            }
        }

        // 3. 解析主键/外键索引
        if (createTable.getIndexes() != null) {
            for (Index index : createTable.getIndexes()) {
                String type = index.getType();
                if ("PRIMARY KEY".equalsIgnoreCase(type)) {
                    index.getColumnsNames().forEach(name ->
                            primaryKeys.add(name.replaceAll("[`\"']", "")));
                } else if (index instanceof ForeignKeyIndex) {
                    ForeignKeyIndex fkIndex = (ForeignKeyIndex) index;
                    ForeignKeyInfo fk = new ForeignKeyInfo();
                    fk.setColumnName(fkIndex.getColumnsNames().get(0).replaceAll("[`\"']", ""));
                    fk.setReferenceTable(fkIndex.getTable().getName().replaceAll("[`\"']", ""));
                    if (fkIndex.getReferencedColumnNames() != null && !fkIndex.getReferencedColumnNames().isEmpty()) {
                        fk.setReferenceColumn(fkIndex.getReferencedColumnNames().get(0).replaceAll("[`\"']", ""));
                    }
                    foreignKeys.add(fk);
                }
            }
        }

        // 4. 关键修复：鲁棒提取表注释（中文表名）
        String tableComment = extractTableComment(createTable);
        tableInfo.setComment((tableComment != null && !tableComment.isEmpty()) ? tableComment : cleanTableName);

        tableInfo.setColumns(columns);
        tableInfo.setPrimaryKeys(primaryKeys);
        tableInfo.setForeignKeys(foreignKeys);
        String result = tableInfo.toString();
        log.debug("最终预处理后的合法SQL:\n{}", result);
        return tableInfo;
    }

    /**
     * 辅助方法：从表选项中精准定位 COMMENT
     */
    private String extractTableComment(CreateTable createTable) {
        List<?> options = createTable.getTableOptionsStrings();
        if (options == null) return null;

        // 1. 将所有选项合并成一个大的字符串，方便统一搜索
        String allOptions = options.stream()
                .map(Object::toString)
                .collect(Collectors.joining(" "));

        log.debug("待解析的表选项字符串: {}", allOptions);

        // 2. 使用正则匹配 COMMENT 后面的内容
        // 匹配模式：COMMENT 后面可能跟着空格或等号，然后是被单引号或双引号包裹的内容
        Pattern pattern = Pattern.compile("(?i)COMMENT\\s*=?\\s*['\"]([^'\"]+)['\"]");
        Matcher matcher = pattern.matcher(allOptions);

        if (matcher.find()) {
            return matcher.group(1); // 直接返回捕获组中的内容（即中文注释）
        }

        // 3. 备选方案：如果正则没搜到，尝试之前的遍历逻辑（适配某些特殊的 JSQLParser 版本）
        for (int i = 0; i < options.size(); i++) {
            String op = options.get(i).toString();
            if ("COMMENT".equalsIgnoreCase(op) && i + 1 < options.size()) {
                return cleanCommentValue(options.get(i + 1).toString());
            }
            if (op.toUpperCase().startsWith("COMMENT")) {
                return cleanCommentValue(op);
            }
        }

        return null;
    }

    private String cleanCommentValue(String val) {
        if (val == null) return "";
        // 移除：COMMENT单词、等号、单引号、双引号、逗号、分号
        return val.replaceAll("(?i)COMMENT|\\s|=|[',;]|\"", "").trim();
    }
}