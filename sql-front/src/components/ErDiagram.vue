<!-- ER图显示组件 -->
<template>
  <el-card class="diagram-output" :body-style="{ padding: '0' }">
    <template #header>
      <div class="card-header">
        <div class="header-left">
          <div class="header-icon-wrapper">
            <el-icon><Connection /></el-icon>
          </div>
          <div class="header-title">
            <h3>ER 图预览</h3>
            <small>Interactive Visualizer</small>
          </div>
        </div>
        <div class="header-right">
          <el-button 
            class="action-btn-refresh"
            @click="autoLayout" 
            :icon="Refresh"
          >
            一键排版
          </el-button>
          <el-button 
            type="success" 
            class="action-btn-export"
            @click="openExportDialog" 
            :icon="Download"
          >
            导出图像
          </el-button>
        </div>
      </div>
    </template>

    <div class="diagram-wrapper">
      <LayoutSettings 
        v-model="showLayoutControls"
        v-model:entitySpacing="entitySpacing"
        v-model:attributeSpacing="attributeSpacing"
        v-model:fontSize="fontSize"
        v-model:backgroundColor="backgroundColor"
        v-model:borderColor="borderColor"
        v-model:textColor="textColor"
        @spacing-change="handleSpacingChange"
        @style-change="handleStyleChange"
        @apply-styles="applyStyles"
        @reset-styles="resetStyles"
      />
      
      <el-button 
        v-if="!showLayoutControls" 
        class="layout-settings-trigger" 
        size="small" 
        @click="showLayoutControls = true"
        :icon="Setting"
      >
        布局设置
      </el-button>

      <div ref="diagramDiv" class="diagram-container"></div>
    </div>

    <ExportDialog 
      v-model="isExportDialogVisible" 
      @confirm="handleExportConfirm"
    />
  </el-card>
</template>


<style lang="scss" scoped>
.diagram-output {
  height: calc(100vh - 120px); // 与左侧 SQL 卡片对齐高度
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #ebeef5;
  background: #fff;
  display: flex;
  flex-direction: column;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);

  &:hover {
    box-shadow: 0 4px 16px 0 rgba(0, 0, 0, 0.08);
  }

  :deep(.el-card__header) {
    background: #ffffff;
    border-bottom: 1px solid #f0f0f0;
    padding: 12px 20px;
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

    .header-icon-wrapper {
      width: 32px;
      height: 32px;
      background: #f0f9eb; // 预览区使用浅绿色系区分
      color: #67c23a;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 18px;
    }

    .header-title {
      display: flex;
      flex-direction: column;
      h3 {
        font-size: 16px;
        font-weight: 600;
        color: #303133;
        margin: 0;
        line-height: 1.2;
      }
      small {
        font-size: 11px;
        color: #909399;
        text-transform: uppercase;
        letter-spacing: 0.5px;
      }
    }
  }

  .header-right {
    display: flex;
    gap: 10px;

    // 排版按钮
    .action-btn-refresh {
      border-radius: 8px;
      background: #ecf5ff;
      color: #409eff;
      border: 1px solid #d9ecff;
      &:hover {
        background: #409eff;
        color: #fff;
      }
    }

    // 导出按钮
    .action-btn-export {
      border-radius: 8px;
      font-weight: 500;
      box-shadow: 0 4px 12px rgba(103, 194, 58, 0.2);
      &:hover {
        transform: translateY(-1px);
        box-shadow: 0 6px 16px rgba(103, 194, 58, 0.3);
      }
    }
  }
}

.diagram-wrapper {
  flex: 1;
  padding: 16px;
  background-color: #f5f7f9; // 稍微深一点的底色突出白色画布
  position: relative;
  overflow: hidden;
}

.layout-settings-trigger {
  position: absolute;
  top: 24px;
  right: 24px;
  z-index: 10;
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.diagram-container {
  width: 100%;
  height: 100%;
  background-color: white;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: inset 0 0 10px rgba(0,0,0,0.02); // 内部微弱阴影增加空间感

  :deep(canvas) {
    outline: none;
    // 强制让网格背景更淡一些，如果 GoJS 未设置的话
  }
}
</style>
<script>
import { ref, onMounted, nextTick } from 'vue'
import { Download, Refresh, Setting } from '@element-plus/icons-vue'
import * as go from 'gojs'
import ExportDialog from './ExportDialog.vue'  // 导入 ExportDialog 组件
import LayoutSettings from './LayoutSettings.vue'  // 导入 LayoutSettings 组件
import DiagramLayout from '../utils/DiagramLayout'  // 导入布局工具类

export default {
  name: 'ErDiagram',
  components: {
    ExportDialog,
    LayoutSettings
  },
  emits: ['export', 'open-export-dialog'],
  setup(props, { emit }) {
    const diagramDiv = ref(null)
    let myDiagram = null
    const isExportDialogVisible = ref(false)  // 添加对话框显示状态
    const showLayoutControls = ref(false)     // 控制布局设置面板显示
    const entitySpacing = ref(180)            // 实体间距默认值
    const attributeSpacing = ref(100)         // 属性间距默认值
    const fontSize = ref(16)                 // 字体大小默认值
    const backgroundColor = ref('#ffffff')   // 背景颜色默认值
    const borderColor = ref('#1890ff')        // 框线颜色默认值
    const textColor = ref('#333333')         // 字体颜色默认值

    onMounted(async () => {
      await nextTick()
      initDiagram()
    })

    const initDiagram = () => {
      if (!diagramDiv.value) {
        console.error('图表容器未找到')
        return
      }

      const $ = go.GraphObject.make

      myDiagram = $(go.Diagram, diagramDiv.value, {
        initialContentAlignment: go.Spot.Center,
        "undoManager.isEnabled": true,
        layout: $(go.ForceDirectedLayout, {
          defaultSpringLength: 50,
          defaultElectricalCharge: 100
        }),
        "toolManager.mouseWheelBehavior": go.ToolManager.WheelZoom,
        "draggingTool.isEnabled": true,
        allowSelect: true,
        allowHorizontalScroll: true,
        allowVerticalScroll: true,
        padding: 40,
        "minScale": 0.25,
        "maxScale": 2,
        "animationManager.isEnabled": true,
        "grid.visible": true,
        "grid.gridCellSize": new go.Size(10, 10),
        "toolManager.dragSelectingTool": $(go.DragSelectingTool, {
          isEnabled: true,
          delay: 50,
          box: $(go.Part,
            { layerName: "Tool" },
            $(go.Shape,
              { strokeWidth: 2, stroke: "blue", fill: "rgba(0,0,255,0.1)" })
          )
        }),
        "toolManager.clickSelectingTool": $(go.ClickSelectingTool, {
          isEnabled: true
        })
      })

      // 启用多选工具
      myDiagram.toolManager.dragSelectingTool = new go.DragSelectingTool()
      myDiagram.toolManager.clickSelectingTool = new go.ClickSelectingTool()

      // 设置多选工具的选项
      myDiagram.toolManager.dragSelectingTool.box = $(go.Part,
        { layerName: "Tool" },
        $(go.Shape,
          { strokeWidth: 2, stroke: "blue", fill: "transparent" })
      )

      // 定义实体节点模板（矩形）
      myDiagram.nodeTemplateMap.add("Entity",
        $(go.Node, "Auto",
          {
            selectionAdornmentTemplate:
              $(go.Adornment, "Auto",
                $(go.Shape, { fill: null, stroke: "blue", strokeWidth: 2 }),
                $(go.Placeholder)
              ),
            locationSpot: go.Spot.Center
          },
          new go.Binding("location", "loc", go.Point.parse).makeTwoWay(go.Point.stringify),
          $(go.Shape, "Rectangle",
            { 
              fill: "white", 
              stroke: "#1890ff", 
              strokeWidth: 2, 
              width: 120, 
              height: 60 
            },
            new go.Binding("fill", "background"),
            new go.Binding("stroke", "border")),
          $(go.TextBlock,
            { 
              margin: 8, 
              font: "bold 16px 微软雅黑", 
              stroke: "#1890ff",
              editable: true
            },
            new go.Binding("text", "name").makeTwoWay(),
            new go.Binding("font", "fontSize", function(size) {
              return `bold ${size || 16}px 微软雅黑`;
            }),
            new go.Binding("stroke", "textColor"))
        ))

      // 定义属性节点模板（椭圆形）
      myDiagram.nodeTemplateMap.add("Attribute",
        $(go.Node, "Auto",
          {
            minSize: new go.Size(60, 40),
            selectionAdornmentTemplate:
              $(go.Adornment, "Auto",
                $(go.Shape, { fill: null, stroke: "blue", strokeWidth: 2 }),
                $(go.Placeholder)
              ),
            locationSpot: go.Spot.Center
          },
          new go.Binding("location", "loc", go.Point.parse).makeTwoWay(go.Point.stringify),
          $(go.Shape, "Ellipse",
            { 
              fill: "white", 
              stroke: "#1890ff", 
              strokeWidth: 2,
              margin: 4
            },
            new go.Binding("fill", "background"),
            new go.Binding("stroke", "border")),
          $(go.TextBlock,
            { 
              margin: 8, 
              font: "14px 微软雅黑", 
              stroke: "#333",
              editable: true,
              _class: new go.Binding("_class", "isPrimaryKey", pk => pk ? "primary-key" : "")
            },
            new go.Binding("text", "name").makeTwoWay(),
            new go.Binding("font", "fontSize", function(size) {
              return `${size || 14}px 微软雅黑`;
            }),
            new go.Binding("stroke", "textColor"))
        ))

      // 定义关系节点模板（菱形）
      myDiagram.nodeTemplateMap.add("Relationship",
        $(go.Node, "Auto",
          {
            selectionAdornmentTemplate:
              $(go.Adornment, "Auto",
                $(go.Shape, { fill: null, stroke: "blue", strokeWidth: 2 }),
                $(go.Placeholder)
              )
          },
          $(go.Shape, "Diamond",
            { 
              fill: "white", 
              stroke: "#1890ff", 
              strokeWidth: 2, 
              width: 60, 
              height: 60 
            },
            new go.Binding("fill", "background"),
            new go.Binding("stroke", "border")),
          $(go.TextBlock,
            { 
              margin: 8, 
              font: "14px 微软雅黑", 
              stroke: "#333",
              editable: true
            },
            new go.Binding("text", "name").makeTwoWay(),
            new go.Binding("font", "fontSize", function(size) {
              return `${size || 14}px 微软雅黑`;
            }),
            new go.Binding("stroke", "textColor"))
        ))

      // 定义连接线模板
      myDiagram.linkTemplate =
        $(go.Link,
          { 
            routing: go.Link.Normal, 
            corner: 5,
            curve: go.Link.JumpOver,
            selectionAdornmentTemplate:
              $(go.Adornment,
                $(go.Shape,
                  { isPanelMain: true, stroke: "blue", strokeWidth: 2 })
              )
          },
          $(go.Shape,
            { 
              strokeWidth: 1.5, 
              stroke: "#1890ff"
            },
            new go.Binding("stroke", "linkColor")),
          $(go.TextBlock,
              { 
                segmentOffset: new go.Point(0, -10), 
                font: "12px 微软雅黑",
                editable: true
              },
              new go.Binding("text", "text").makeTwoWay(),
              new go.Binding("stroke", "linkTextColor"))
        )
    }

    const updateDiagram = (tables) => {
      if (!myDiagram) {
        console.error('图表未初始化')
        return
      }

      const nodeDataArray = []
      const linkDataArray = []

      // 首先收集所有的外键关系，使用 Map 来存储关系的方向
      const foreignKeyRelations = new Map()
      tables.forEach(table => {
        table.foreignKeys?.forEach(fk => {
          if (fk.referenceTable) {
            // 存储关系的方向：from -> to
            foreignKeyRelations.set(`${table.tableName}-${fk.referenceTable}`, true)
          }
        })
      })

      tables.forEach(table => {
        // 添加实体节点
        nodeDataArray.push({
          category: "Entity",
          key: table.tableName,
          name: removeQuotes(table.comment) || table.tableName
        })

        // 添加属性节点
        table.columns.forEach(col => {
          const attrKey = `${table.tableName}-${col.name}`
          nodeDataArray.push({
            category: "Attribute",
            key: attrKey,
            name: removeQuotes(col.comment) || col.name,
            isPrimaryKey: table.primaryKeys.includes(col.name)
          })
          // 添加属性与实体的连接
          linkDataArray.push({
            from: table.tableName,
            to: attrKey
          })
        })

        // 添加外键关系节点
        table.foreignKeys?.forEach(fk => {
          // 只有当外键关系存在且还未处理过时才添加关联
          const relationKey = `${table.tableName}-${fk.referenceTable}`
          if (fk.referenceTable && foreignKeyRelations.get(relationKey)) {
            const relationshipKey = `${relationKey}-relationship`
            nodeDataArray.push({
              category: "Relationship",
              key: relationshipKey,
              name: "关联"
            })
            // 添加关系与实体的连接（使用实线）
            linkDataArray.push({
              from: table.tableName,
              to: relationshipKey,
              text: "N"
            })
            linkDataArray.push({
              from: relationshipKey,
              to: fk.referenceTable,
              text: "1"
            })
            // 标记这个关系已经处理过
            foreignKeyRelations.delete(relationKey)
          }
        })
      })

      myDiagram.model = new go.GraphLinksModel(nodeDataArray, linkDataArray)
      console.log('节点数据:', nodeDataArray)
      console.log('连接数据:', linkDataArray)
      myDiagram.layoutDiagram(true)
      myDiagram.zoomToFit()
      myDiagram.contentAlignment = go.Spot.Center
    }

    // 去除字符串两端的单引号
    const removeQuotes = (str) => {
      if (!str) return str;
      return str.replace(/^'|'$/g, '');
    }

    const handleExport = () => {
      if (!myDiagram.nodes.count) {
        return
      }
      emit('export', myDiagram)
    }

    const openExportDialog = () => {
      isExportDialogVisible.value = true  // 显示对话框
    }

    const handleExportConfirm = (options) => {
      console.log('Export options:', options)
      if (!myDiagram.nodes.count) {
        return
      }

      // 设置背景颜色
      const originalBackground = myDiagram.div.style.background
      myDiagram.div.style.background = options.background === 'transparent' ? 'transparent' : 'white'

      // 创建临时画布
      const canvas = document.createElement('canvas')
      const context = canvas.getContext('2d')
      const bounds = myDiagram.documentBounds
      
      // 设置画布大小
      canvas.width = bounds.width
      canvas.height = bounds.height

      // 如果选择透明背景，确保上下文是透明的
      if (options.background === 'transparent') {
        context.clearRect(0, 0, canvas.width, canvas.height)
      } else {
        context.fillStyle = 'white'
        context.fillRect(0, 0, canvas.width, canvas.height)
      }

      // 将图表绘制到画布上
      const imageData = myDiagram.makeImageData({
        scale: 1,
        background: options.background === 'transparent' ? null : 'white',
        type: options.format === 'jpeg' ? 'image/jpeg' : 'image/png',
        maxSize: new go.Size(Infinity, Infinity)
      })

      // 生成时间戳文件名
      const now = new Date()
      const timestamp = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}${String(now.getSeconds()).padStart(2, '0')}`
      
      // 创建下载链接
      const link = document.createElement('a')
      link.download = `${timestamp}ER图.${options.format}`
      link.href = imageData
      
      // 触发下载
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)

      // 恢复原始背景
      myDiagram.div.style.background = originalBackground
    }

    // 使用布局工具类实现自动排版
    const autoLayout = () => {
      if (!myDiagram) return
      
      // 收集布局选项
      const layoutOptions = {
        entitySpacing: entitySpacing.value,
        attributeSpacing: attributeSpacing.value,
        fontSize: fontSize.value,
        backgroundColor: backgroundColor.value,
        borderColor: borderColor.value,
        textColor: textColor.value
      }
      
      // 调用布局工具类执行自动排版
      DiagramLayout.autoLayout(myDiagram, layoutOptions)
    }

    const handleStyleChange = () => {
      // 仅当用户点击"应用样式"按钮时才应用样式
    }

    const applyStyles = () => {
      // 应用样式
      autoLayout()
      // 关闭设置面板
      showLayoutControls.value = false
    }

    const resetStyles = () => {
      // 重置样式到默认值
      fontSize.value = 16
      backgroundColor.value = '#ffffff'
      borderColor.value = '#1890ff'
      textColor.value = '#333333'
      entitySpacing.value = 180
      attributeSpacing.value = 100
      autoLayout()
    }

    const handleSpacingChange = () => {
      // 当滑动条值改变时，应用新的布局
      autoLayout()
    }

    return {
      diagramDiv,
      handleExport,
      updateDiagram,
      openExportDialog,
      isExportDialogVisible,
      handleExportConfirm,
      autoLayout,
      Download,
      Refresh,
      Setting,
      showLayoutControls,
      entitySpacing,
      attributeSpacing,
      handleSpacingChange,
      fontSize,
      backgroundColor,
      borderColor,
      textColor,
      handleStyleChange,
      applyStyles,
      resetStyles
    }
  }
}
</script>
