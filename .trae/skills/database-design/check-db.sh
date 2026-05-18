#!/bin/bash

echo "=========================================="
echo "  数据库配置检查"
echo "=========================================="
echo ""

# 检查配置文件
CONFIG_FILES=(
    "src/main/resources/application.yml"
    "src/main/resources/application.properties"
    "application.yml"
    "application.properties"
)

FOUND_CONFIG=false

for config in "${CONFIG_FILES[@]}"; do
    if [ -f "$config" ]; then
        echo "✓ 发现配置文件: $config"
        FOUND_CONFIG=true
        
        # 检查数据库配置
        if grep -q "datasource" "$config"; then
            echo "✓ 包含数据源配置"
            
            # 检查连接池配置
            if grep -q "hikari" "$config" || grep -q "druid" "$config"; then
                echo "✓ 配置了连接池"
            else
                echo "⚠️  建议配置连接池（HikariCP 或 Druid）"
            fi
        fi
        echo ""
        break
    fi
done

if [ "$FOUND_CONFIG" = false ]; then
    echo "ℹ️  未找到配置文件"
fi

# 扫描 SQL 文件
echo "=========================================="
echo "  SQL 文件扫描"
echo "=========================================="
echo ""

SQL_FILES=$(find . -name "*.sql" -not -path "*/target/*" -not -path "*/.git/*" 2>/dev/null)

if [ -n "$SQL_FILES" ]; then
    SQL_COUNT=$(echo "$SQL_FILES" | wc -l | tr -d ' ')
    echo "✓ 发现 $SQL_COUNT 个 SQL 文件"
    echo ""
    
    # 检查是否有 CREATE TABLE 语句
    CREATE_TABLES=$(grep -rni "CREATE TABLE" --include="*.sql" . 2>/dev/null | wc -l | tr -d ' ')
    if [ "$CREATE_TABLES" -gt 0 ]; then
        echo "✓ 发现 $CREATE_TABLES 个建表语句"
    fi
    
    # 检查索引
    INDEXES=$(grep -rni "CREATE INDEX\|ADD INDEX" --include="*.sql" . 2>/dev/null | wc -l | tr -d ' ')
    if [ "$INDEXES" -gt 0 ]; then
        echo "✓ 发现 $INDEXES 个索引定义"
    else
        echo "⚠️  未发现索引定义，建议为常用查询字段添加索引"
    fi
else
    echo "ℹ️  未发现 SQL 文件"
fi

echo ""
echo "=========================================="
echo "  建议"
echo "=========================================="
echo ""
echo "1. 使用合适的字段类型（金额用 DECIMAL）"
echo "2. 为常用查询字段添加索引"
echo "3. 避免 SELECT * 查询"
echo "4. 注意 N+1 查询问题"
echo "5. 使用连接池优化数据库连接"
echo ""

exit 0
