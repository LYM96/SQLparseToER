<template>
  <el-card class="sql-input-card" :body-style="{ padding: '0' }">
    <template #header>
      <div class="card-header">
        <div class="header-left">
          <div class="header-icon-wrapper">
            <el-icon><EditPen /></el-icon>
          </div>
          <div class="header-title">
            <h3>SQL 定义</h3>
            <small>Input DDL Scripts</small>
          </div>
        </div>
        <el-button 
          type="primary" 
          @click="handleParseSql" 
          :icon="ArrowRight"
          class="parse-btn"
        >
          解析并生成
        </el-button>
      </div>
    </template>

    <div class="editor-wrapper">
      <div class="editor-container">
        <el-input
          v-model="sqlInput"
          type="textarea"
          placeholder="-- 请在此输入 CREATE TABLE 语句...
CREATE TABLE `user` (
  `id` bigint PRIMARY KEY,
  `name` varchar(255) COMMENT '姓名'
);"
          resize="none"
          spellcheck="false"
        />
      </div>
      <div class="editor-footer">
        <span>支持 MySQL / Oracle / PostgreSQL 语法</span>
        <span class="char-count">{{ sqlInput.length }} characters</span>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, defineEmits } from 'vue'
import { ArrowRight, EditPen } from '@element-plus/icons-vue'

const sqlInput = ref('')
const emit = defineEmits(['parse-sql'])

const handleParseSql = () => {
  emit('parse-sql', sqlInput.value)
}
</script>
<style lang="scss" scoped>
/* 1. Header 标题区域样式优化 */
.header-title {
  display: flex;
  flex-direction: column;
  margin-left: 4px; // 稍微拉开与图标的距离

  h3 {
    font-size: 18px;    /* 修正拼写：font-size */
    font-weight: 600;   /* 修正拼写：font-weight */
    color: #303133;
    margin: 0;          /* 移除 h3 默认边距 */
    line-height: 1.2;
  }

  small {
    font-size: 11px;
    color: #909399;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    margin-top: 2px;
  }
}

/* 2. 左侧图标背景 (图片中的蓝色小方块) */
.header-icon-wrapper {
  width: 32px;
  height: 32px;
  background: #ecf5ff; // 浅蓝色背景
  color: #409eff;      // 蓝色图标
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  transition: all 0.3s;

  &:hover {
    background: #409eff;
    color: #fff;
  }
}

/* 3. 解析按钮样式 (图片中的带阴影蓝色按钮) */
.parse-btn {
  padding: 8px 16px;
  border-radius: 8px;
  font-weight: 500;
  /* 增加图片中的那种轻微呼吸感阴影 */
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
  transition: all 0.2s;
  
  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 6px 16px rgba(64, 158, 255, 0.4);
  }

  &:active {
    transform: translateY(0);
  }
}

/* 4. 基础容器布局 (确保撑满) */
.sql-input-card {
  height: 100%; 
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #ebeef5;
  background: #fff;
  display: flex;
  flex-direction: column;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05); // 增加整体轻微阴影

  :deep(.el-card__header) {
    background: #ffffff; // 图片中背景较白
    border-bottom: 1px solid #f0f0f0;
    padding: 16px 20px;
  }

  :deep(.el-card__body) {
    flex: 1;
    display: flex;
    flex-direction: column;
    padding: 0;
    overflow: hidden;
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

/* 5. 编辑器部分保持之前的高端深色风格 */
.editor-wrapper {
  flex: 1;
  display: flex;
  position: relative;
  background-color: #282c34;
  padding-bottom: 28px;
  overflow: hidden;
}

.line-numbers {
  flex-shrink: 0;
  width: 45px;
  padding: 20px 0;
  text-align: center;
  background-color: #21252b;
  color: #5c6370;
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
  line-height: 1.6;
  user-select: none;
  border-right: 1px solid #181a1f;
}

.editor-container {
  flex: 1;
  height: 100%;

  :deep(.el-textarea) {
    height: 100%;
    .el-textarea__inner {
      height: 100% !important;
      min-height: 100% !important;
      padding: 20px;
      border: none;
      background-color: transparent;
      color: #abb2bf;
      font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
      font-size: 14px;
      line-height: 1.6;
      border-radius: 0;
      box-shadow: none;
    }
  }
}

.editor-footer {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 28px;
  background: #21252b;
  border-top: 1px solid #181a1f;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 15px;
  color: #5c6370;
  font-size: 11px;
  z-index: 10;
}
</style>