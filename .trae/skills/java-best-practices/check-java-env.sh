#!/bin/bash

echo "=========================================="
echo "  Java 环境检查"
echo "=========================================="
echo ""

if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    echo "✓ Java 版本: $JAVA_VERSION"
    
    # 检查 Java 17+
    MAJOR_VERSION=$(echo $JAVA_VERSION | cut -d. -f1)
    if [ "$MAJOR_VERSION" -ge 17 ]; then
        echo "✓ 版本符合要求（Java 17+）"
    else
        echo "⚠️  建议升级到 Java 17 或更高版本"
    fi
else
    echo "❌ Java 未安装"
fi

echo ""

# 检查 Maven
if command -v mvn &> /dev/null; then
    MVN_VERSION=$(mvn -version | head -1)
    echo "✓ $MVN_VERSION"
else
    echo "ℹ️  Maven 未安装"
fi

echo ""
echo "=========================================="

exit 0
