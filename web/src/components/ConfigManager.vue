<template>
  <a-layout>
    <a-layout-content>
      <div class="layout-content-margin">
        <!-- 查询框 -->
        <div class="table-search">
          <a-form layout="horizontal" :colon="false" :labelCol="{ span: 6 }" :wrapperCol="{ span: 16 }">
            <a-row class="filter-flex">
              <a-col :xxl="8" :xl="8" :lg="12" :xs="24">
                <a-form-item :label="`类别`">
                  <a-select v-model="query.provider" @change="getData()">
                    <a-select-option key="" value="">
                      <span>全部</span>
                    </a-select-option>
                    <a-select-option v-for="item in typeOptions" :key="item.value">
                      <span>{{ item.label }}</span>
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :xl="8" :lg="12" :xs="24" v-for="item in queryFilter" :key="item.index">
                <a-form-item :label="item.label">
                  <a-input-search v-model="query[item.index]" placeholder="请输入" allow-clear @search="getData()" />
                </a-form-item>
              </a-col>
              <!-- 添加模型类型筛选，仅在LLM配置时显示 -->
              <a-col :xxl="8" :xl="8" :lg="12" :xs="24" v-if="configType === 'llm'">
                <a-form-item label="模型类型">
                  <a-select v-model="query.modelType" @change="getData()">
                    <a-select-option value="">
                      <span>全部</span>
                    </a-select-option>
                    <a-select-option value="chat">对话模型</a-select-option>
                    <a-select-option value="vision">视觉模型</a-select-option>
                    <a-select-option value="intent">意图模型</a-select-option>
                    <a-select-option value="embedding">向量模型</a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </div>
        <!-- 表格数据 -->
        <a-card :bodyStyle="{ padding: 0 }" :bordered="false">
          <a-tabs defaultActiveKey="1" :activeKey="activeTabKey" @change="handleTabChange"
            :tabBarStyle="{ margin: '0 0 0 15px' }">
            <a-tab-pane key="1" :tab="`${configTypeInfo.label}列表`">
              <a-table :columns="getColumns" :dataSource="configItems" :loading="loading" :pagination="pagination"
                rowKey="configId" :scroll="{ x: 800 }" size="middle">
                <template slot="configDesc" slot-scope="text">
                  <a-tooltip :title="text" :mouseEnterDelay="0.5" placement="leftTop">
                    <span v-if="text">{{ text }}</span>
                    <span v-else style="padding: 0 50px">&nbsp;&nbsp;&nbsp;</span>
                  </a-tooltip>
                </template>
                <!-- 模型应该输出不同颜色的标识 -->
                <template v-if="configType == 'llm'" slot="modelType" slot-scope="text">
                  <a-tag v-if="text === 'chat'" color="blue">对话模型</a-tag>
                  <a-tag v-else-if="text === 'vision'" color="purple">视觉模型</a-tag>
                  <a-tag v-else-if="text === 'intent'" color="orange">意图模型</a-tag>
                  <a-tag v-else-if="text === 'embedding'" color="green">向量模型</a-tag>
                  <span v-else>-</span>
                </template>
                <!-- 添加默认标识列的自定义渲染 -->
                <template slot="isDefault" slot-scope="text">
                  <a-tag v-if="text == 1" color="green">默认</a-tag>
                  <span v-else>-</span>
                </template>
                <template slot="operation" slot-scope="text, record">
                  <a-space>
                    <a href="javascript:" @click="edit(record)">编辑</a>
                    <!-- 添加设为默认按钮，但在TTS中不显示 -->
                    <a v-if="configType !== 'tts' && record.isDefault != 1" href="javascript:"
                      :disabled="record.isDefault == 1" @click="setAsDefault(record)">设为默认</a>
                    <a-popconfirm :title="`确定要删除这个${configTypeInfo.label}配置吗?`"
                      @confirm="deleteConfig(record.configId)">
                      <a v-if="record.isDefault != 1" href="javascript:" style="color: #ff4d4f">删除</a>
                    </a-popconfirm>
                  </a-space>
                </template>
              </a-table>
            </a-tab-pane>
            <a-tab-pane key="2" :tab="`创建${configTypeInfo.label}`">
              <a-form layout="horizontal" :form="configForm" :colon="false" @submit="handleSubmit"
                style="padding: 10px 24px">
                <a-row :gutter="20">
                  <a-col :xl="8" :lg="12" :xs="24">
                    <a-form-item :label="`${configTypeInfo.label}类别`">
                      <a-select v-decorator="[
                        'provider',
                        { rules: [{ required: true, message: `请选择${configTypeInfo.label}类别` }] }
                      ]" :placeholder="`请选择${configTypeInfo.label}类别`" @change="handleTypeChange">
                        <a-select-option v-for="item in typeOptions" :key="item.value" :value="item.value">
                          {{ item.label }}
                        </a-select-option>
                      </a-select>
                    </a-form-item>
                  </a-col>
                  <a-col :xl="8" :lg="12" :xs="24">
                    <a-form-item :label="`${configTypeInfo.label}名称`">
                      <!-- 如果是 llm 且有 currentType，变为可输入的下拉框 -->
                      <a-select v-if="configType === 'llm' && currentType"
                        v-decorator="[
                          'configName',
                          { rules: [{ required: true, message: `请输入${configTypeInfo.label}名称` }] }
                        ]"
                        showSearch
                        allowClear
                        :placeholder="`请输入${configTypeInfo.label}名称`"
                        :options="modelOptions"
                        :filterOption="modelFilterOption"
                        @search="handleModelInputChange"
                        @change="handleModelChange"
                        @blur="handleModelBlur">
                      </a-select>
                      <!-- 如果不是 llm 或没有 currentType，保留原来的输入框 -->
                      <a-input v-else
                        v-decorator="[
                          'configName',
                          { rules: [{ required: true, message: `请输入${configTypeInfo.label}名称` }] }
                        ]"
                        autocomplete="off"
                        :placeholder="`请输入${configTypeInfo.label}名称`" />
                    </a-form-item>
                  </a-col>
                  <!-- 添加模型类型选择，仅在LLM配置时显示 -->
                  <a-col :xl="8" :lg="12" :xs="24" v-if="configType === 'llm'">
                    <a-form-item label="模型类型">
                      <a-select v-decorator="[
                        'modelType',
                        { initialValue: 'chat', rules: [{ required: true, message: '请选择模型类型' }] }
                      ]" placeholder="请选择模型类型">
                        <a-select-option value="chat">对话模型</a-select-option>
                        <a-select-option value="vision">视觉模型</a-select-option>
                        <a-select-option value="intent">意图模型</a-select-option>
                        <a-select-option value="embedding">向量模型</a-select-option>
                      </a-select>
                    </a-form-item>
                  </a-col>
                </a-row>
                <a-form-item :label="`${configTypeInfo.label}描述`">
                  <a-textarea v-decorator="['configDesc']" :placeholder="`请输入${configTypeInfo.label}描述`" :rows="4" />
                </a-form-item>

                <!-- 添加是否默认的开关，但在TTS中不显示 -->
                <a-form-item v-if="configType !== 'tts'" :label="`设为默认${configTypeInfo.label}`">
                  <a-switch v-decorator="[
                    'isDefault',
                    { valuePropName: 'checked', initialValue: false }
                  ]" />
                  <span style="margin-left: 8px; color: #999;">设为默认后将优先使用此配置</span>
                </a-form-item>

                <a-divider>参数配置</a-divider>
                <a-space direction="vertical" style="width: 100%">
                  <a-card v-if="currentType" size="small" :bodyStyle="{ 'background-color': '#fafafa' }"
                    :bordered="false">
                    <a-row :gutter="20">
                      <!-- 根据选择的模型类别动态显示参数配置 -->
                      <template v-for="field in currentTypeFields">
                        <a-col :key="field.name" :xl="field.span || 12" :lg="12" :xs="24">
                          <a-form-item :label="field.label" style="margin-bottom: 24px">
                            <a-input v-decorator="[
                              field.name,
                              { rules: [{ required: field.required, message: `请输入${field.label}` }] }
                            ]" :placeholder="field.placeholder || `请输入${field.label}`" :type="field.inputType || 'text'"
                             @change="getModelList()">
                              <template v-if="field.suffix" slot="suffix">
                                <span style="color: #999">{{ field.suffix }}</span>
                              </template>
                            </a-input>
                            <!-- 字段帮助提示 -->
                            <div v-if="field.help" class="field-help">
                              <a-icon type="question-circle" theme="twoTone" />
                              {{ field.help }}
                            </div>
                          </a-form-item>
                        </a-col>
                      </template>
                    </a-row>
                  </a-card>
                  <a-card v-else :bodyStyle="{ 'background-color': '#fafafa' }" :bordered="false">
                    <a-empty :description="`请先选择${configTypeInfo.label}类别`" />
                  </a-card>

                  <a-form-item>
                    <a-button type="primary" html-type="submit">
                      {{ editingConfigId ? `更新${configTypeInfo.label}` : `创建${configTypeInfo.label}` }}
                    </a-button>
                    <a-button style="margin-left: 8px" @click="resetForm">
                      取消
                    </a-button>
                    <!-- 测试连接按钮，仅在LLM配置时显示 -->
                    <a-button v-if="configType === 'llm'" 
                              type="primary" 
                              style="margin-left: 8px; background-color: #52c41a; border-color: #52c41a"
                              :loading="testLoading" 
                              @click="testModelConnection">
                      测试
                    </a-button>
                    <!-- 测试结果显示 -->
                    <span v-if="testStatus && configType === 'llm'" style="margin-left: 12px">
                      <span v-if="testStatus === 'success'" style="color: #52c41a; font-weight: 500">
                        ✓ 服务连接正常
                      </span>
                      <span v-if="testStatus === 'error'" style="color: #f5222d; font-weight: 500">
                        ✗ 服务连接失败
                      </span>
                    </span>
                  </a-form-item>
                </a-space>
              </a-form>
            </a-tab-pane>
          </a-tabs>
        </a-card>
      </div>
    </a-layout-content>
  </a-layout>
</template>

<script>
import axios from '@/services/axios'
import api from '@/services/api'
import mixin from '@/mixins/index'
import { configTypeMap } from '@/config/providerConfig'

export default {
  name: 'ConfigManager',
  mixins: [mixin],
  props: {
    // 配置类型：llm, stt, tts
    configType: {
      type: String,
      required: true,
      validator: value => ['llm', 'stt', 'tts'].includes(value)
    }
  },
  data() {
    return {
      // 查询框
      query: {
        provider: "",
        modelType: ""
      },
      queryFilter: [
        {
          label: "名称",
          value: "",
          index: "configName",
        },
      ],
      activeTabKey: '1', // 当前激活的标签页
      configForm: null,
      configItems: [],
      editingConfigId: null,
      currentType: '',
      loading: false,
      
      // 测试连接相关状态
      testLoading: false,    // 测试按钮加载状态
      testStatus: '',        // 测试状态: 'success', 'error', ''
      testMessage: '',       // 测试结果消息

      modelOptions: [], // 存储模型下拉框选项

      columns: [
        {
          title: '类别',
          dataIndex: 'provider',
          key: 'provider',
          width: 200,
          align: 'center',
          customRender: (text) => {
            const provider = this.typeOptions.find(item => item.value === text);
            return provider ? provider.label : text;
          },
          ellipsis: true,
        },
        {
          title: '名称',
          dataIndex: 'configName',
          key: 'configName',
          width: 200,
          align: 'center',
        },
        {
          title: '模型类型',
          dataIndex: 'modelType',
          key: 'modelType',
          width: 120,
          align: 'center',
          scopedSlots: { customRender: 'modelType' },
        },
        {
          title: '描述',
          dataIndex: 'configDesc',
          scopedSlots: { customRender: 'configDesc' },
          key: 'configDesc',
          align: 'center',
          width: 200,
          ellipsis: true,
        },
        // 添加默认标识列
        {
          title: '默认',
          dataIndex: 'isDefault',
          key: 'isDefault',
          width: 80,
          align: 'center',
          scopedSlots: { customRender: 'isDefault' }
        },
        {
          title: '创建时间',
          dataIndex: 'createTime',
          key: 'createTime',
          width: 180,
          align: 'center'
        },
        {
          title: '操作',
          dataIndex: 'operation',
          key: 'operation',
          width: 180,
          align: 'center',
          fixed: 'right',
          scopedSlots: { customRender: 'operation' }
        }
      ]
    }
  },
  computed: {
    // 当前配置类型的信息
    configTypeInfo() {
      return configTypeMap[this.configType] || {};
    },
    // 当前配置类型的选项
    typeOptions() {
      return this.configTypeInfo.typeOptions || [];
    },
    // 当前选择的类别对应的参数字段
    currentTypeFields() {
      const typeFieldsMap = this.configTypeInfo.typeFields || {};
      return typeFieldsMap[this.currentType] || [];
    },
    // 根据配置类型获取适当的列
    getColumns() {
      if (this.configType === 'tts') {
        // 对于TTS，过滤掉isDefault列
        return this.columns.filter(col => col.key !== 'isDefault' && col.key !== 'modelType');
      } else if (this.configType !== 'llm') {
        return this.columns.filter(col => col.key !== 'modelType')
      }
      return this.columns;
    }
  },
  created() {
    // 创建表单实例
    this.configForm = this.$form.createForm(this, {
      onValuesChange: (props, values) => {
        if (values.provider && values.provider !== this.currentType) {
          this.currentType = values.provider;
        }
      }
    });
  },
  mounted() {
    this.getData()
  },
  methods: {
    getModelList() {

      const formValues = this.configForm.getFieldsValue();
      const apiKey = formValues.apiKey;
      const apiUrl = formValues.apiUrl;

      // 检查是否输入了必要的参数
      if (!apiKey || !apiUrl) {
        return;
      }
      axios
        .post({
          url: api.config.getModels,
          data: {
            ...formValues
          }
        })
        .then(res => {
          if (res.code === 200) {
            this.modelOptions = res.data.map((item) => ({
              value: item.id,
              label: item.id,
            }));
          }
        })
        .catch(() => {
          this.showError();
        })
    },

    filterOption(input, option) {
      return option.label.toLowerCase().includes(input.toLowerCase());
    },

    // 处理输入变化
    handleModelInputChange(value) {
      this.$nextTick(() => {
        // 手动绑定输入的值到表单字段
        setTimeout(() => {
          this.configForm.setFieldsValue({
            configName: value
          });
        }, 0);
      });
    },

    // 处理选项变化
    handleModelChange(value) {
      // 如果用户选择了一个选项，直接更新表单字段
      this.$nextTick(() => {
        // 手动绑定输入的值到表单字段
        setTimeout(() => {
          this.configForm.setFieldsValue({
            configName: value
          });
        }, 0);
      });
    },

    // 处理失去焦点时的逻辑
    handleModelBlur() {
      const value = this.configForm.getFieldValue('configName');
      // 如果输入的值不在选项列表中，保留用户输入的值
      this.$nextTick(() => {
        // 手动绑定输入的值到表单字段
        setTimeout(() => {
          this.configForm.setFieldsValue({
            configName: value
          });
        }, 0);
      });
    },

    // 处理标签页切换
    handleTabChange(key) {
      this.activeTabKey = key;
      this.resetForm();
    },

    // 处理类别变化
    handleTypeChange(value) {
      console.log('选择的类别:', value);
      this.currentType = value;

      // 由于使用了v-decorator，不需要手动设置表单值
      // 但需要清除之前的参数值
      const { configForm } = this;
      const formValues = configForm.getFieldsValue();

      // 创建一个新的表单值对象，只保留基本信息
      const newValues = {
        provider: value,
        configName: formValues.configName,
        configDesc: formValues.configDesc
      };

      // 如果不是TTS，添加isDefault字段
      if (this.configType !== 'tts') {
        newValues.isDefault = formValues.isDefault;
      }

      // 清除所有可能的参数字段
      const allParamFields = ['apiKey', 'apiUrl', 'apiSecret', 'appId', 'secretKey', 'region'];
      allParamFields.forEach(field => {
        newValues[field] = undefined;
      });

      //填写llm默认url
      if (this.configType === 'llm') {
        newValues.modelType = formValues.modelType || 'chat';
        const apiUrlField = configTypeMap.llm.typeFields[value].find(item => item.name === 'apiUrl');
        if (apiUrlField && apiUrlField.defaultUrl) {
          newValues.apiUrl = apiUrlField.defaultUrl;
        }
      }

      // 为所有配置类型填写默认URL
      const typeFields = this.configTypeInfo.typeFields || {};
      const currentTypeFields = typeFields[value] || [];
      currentTypeFields.forEach(field => {
        if (field.defaultUrl && field.name === 'apiUrl') {
          newValues[field.name] = field.defaultUrl;
        }
      });

      // 重置表单
      this.$nextTick(() => {
        // 设置新的表单值
        setTimeout(() => {
          configForm.setFieldsValue(newValues);
        }, 0);
      });
    },

    // 获取配置列表
    getData() {
      this.loading = true;
      axios
        .get({
          url: api.config.query,
          data: {
            page: this.pagination.page,
            pageSize: this.pagination.pageSize,
            configType: this.configType,
            ...this.query
          }
        })
        .then(res => {
          if (res.code === 200) {
            this.configItems = res.data.list
            this.pagination.total = res.data.total
          } else {
            this.$message.error(res.message)
          }
        })
        .catch(() => {
          this.showError();
        })
        .finally(() => {
          this.loading = false
        })
    },

    // 提交表单
    handleSubmit(e) {
      e.preventDefault()
      this.configForm.validateFields((err, values) => {
        if (!err) {

          if (this.configType === 'llm') {
            // 校验 configName 是否为英文、数字或者它们的组合
            const configName = values.configName;
            const containsChineseRegex = /[\u4e00-\u9fa5]/; // 检测是否包含中文字符
            if (containsChineseRegex.test(configName)) {
              this.$message.error('模型名称不能随意输入，请输入正确的模型名称，例如：deepseek-chat、qwen-plus官方名称');
              return;
            }
          }
          
          // 处理可能的URL后缀重复问题
          const currentType = values.provider;
          const typeFields = this.configTypeInfo.typeFields || {};
          const apiUrlField = (typeFields[currentType] || []).find(field => field.name === 'apiUrl');

          if (apiUrlField && apiUrlField.suffix) {
            const suffix = apiUrlField.suffix;
            // 检查URL是否已经以后缀结尾，如果是则不再添加
            if (values.apiUrl.endsWith(suffix)) {
              // 移除URL末尾的后缀部分
              values.apiUrl = values.apiUrl.substring(0, values.apiUrl.length - suffix.length);
            }
          }

          // 将开关值转换为数字，但TTS不需要处理isDefault
          const formData = {
            configId: this.editingConfigId,
            configType: this.configType,
            ...values
          };

          // 只有非TTS类型才处理isDefault
          if (this.configType !== 'tts') {
            formData.isDefault = values.isDefault ? 1 : 0;
          }
          this.loading = true

          const url = this.editingConfigId
            ? api.config.update
            : api.config.add

          axios
            .post({
              url,
              data: formData
            })
            .then(res => {
              if (res.code === 200) {
                this.$message.success(
                  this.editingConfigId ? '更新成功' : '创建成功'
                )
                this.resetForm()
                this.getData()
                // 成功后切换到列表页
                this.activeTabKey = '1'
              } else {
                this.$message.error(res.message)
              }
            })
            .catch(() => {
              this.showError();
            })
            .finally(() => {
              this.loading = false
            })
        }
      })
    },

    // 编辑配置
    edit(record) {
      this.editingConfigId = record.configId
      this.currentType = record.provider || '';

      // 切换到创建配置标签页
      this.activeTabKey = '2'

      this.$nextTick(() => {
        const { configForm } = this

        // 设置表单值，使用setTimeout确保表单已渲染
        setTimeout(() => {
          const formValues = { ...record };

          // 只有非TTS类型才设置isDefault
          if (this.configType !== 'tts') {
            formValues.isDefault = record.isDefault == 1;
          }

          configForm.setFieldsValue(formValues);
          this.getModelList();
        }, 0);
      })
    },

    // 设置为默认配置
    setAsDefault(record) {
      // TTS不应该有这个功能，但为了安全起见，再次检查
      if (this.configType === 'tts') return;

      this.$confirm({
        title: `确定要将此${this.configTypeInfo.label}设为默认吗？`,
        content: `设为默认后，系统将优先使用此${this.configTypeInfo.label}配置，原默认${this.configTypeInfo.label}将被取消默认状态。`,
        okText: '确定',
        cancelText: '取消',
        onOk: () => {
          this.loading = true;
          axios
            .post({
              url: api.config.update,
              data: {
                configId: record.configId,
                configType: this.configType,
                modelType: this.configType === 'llm' ? record.modelType : null,
                isDefault: 1
              }
            })
            .then(res => {
              if (res.code === 200) {
                this.$message.success(`已将"${record.configName}"设为默认${this.configTypeInfo.label}`);
                this.getData();
              } else {
                this.$message.error(res.message)
              }
            })
            .catch(() => {
              this.showError();
            })
            .finally(() => {
              this.loading = false;
            });
        }
      });
    },

    // 删除配置
    deleteConfig(configId) {
      this.loading = true
      axios
        .post({
          url: api.config.update,
          data: {
            configId: configId,
            configType: this.configType,
            state: 0
          }
        })
        .then(res => {
          if (res.code === 200) {
            this.$message.success(`删除${this.configTypeInfo.label}配置成功`)
            this.getData()
          } else {
            this.$message.error(res.message)
          }
        })
        .catch(() => {
          this.showError();
        })
        .finally(() => {
          this.loading = false
        })
    },

    // 测试模型连接
    async testModelConnection() {
      this.testLoading = true;
      this.testStatus = '';
      this.testMessage = '';
      
      try {
        // 获取表单值
        const formValues = this.configForm.getFieldsValue();
        
        // 表单验证
        if (!formValues.apiKey || !formValues.apiUrl) {
          this.testStatus = 'error';
          this.testMessage = 'API密钥或API地址未填写';
          console.error('测试失败: API密钥或API地址未填写', {
            apiKey: formValues.apiKey ? '已填写' : '未填写',
            apiUrl: formValues.apiUrl ? '已填写' : '未填写',
            formValues: formValues
          });
          this.$message.error('请先填写API密钥和API地址');
          return;
        }
        
        console.log('开始测试模型连接...', {
          provider: formValues.provider,
          apiUrl: formValues.apiUrl,
          apiKey: formValues.apiKey ? `${formValues.apiKey.substring(0, 10)}...` : '未设置'
        });
        
        // 调用测试接口
        const res = await axios.post({
          url: api.config.testConnection,
          data: formValues
        });
        
        if (res.code === 200) {
          this.testStatus = 'success';
          this.testMessage = res.message || '连接成功';
          console.log('模型连接测试成功:', {
            message: res.message,
            data: res.data
          });
          this.$message.success('连接测试成功');
        } else {
          this.testStatus = 'error';
          this.testMessage = res.message || '连接失败';
          console.error('模型连接测试失败:', {
            code: res.code,
            message: res.message,
            data: res.data
          });
          this.$message.error(`连接测试失败: ${res.message}`);
        }
        
      } catch (error) {
        this.testStatus = 'error';
        this.testMessage = error.message || '连接测试异常';
        
        // 详细错误信息输出到控制台
        console.error('模型连接测试异常:', {
          message: error.message,
          response: error.response,
          responseData: error.response && error.response.data,
          status: error.response && error.response.status,
          statusText: error.response && error.response.statusText,
          config: error.config,
          stack: error.stack,
          fullError: error
        });
        
        // 根据错误类型给出更友好的提示
        let errorMessage = '连接测试失败';
        if (error.response) {
          const status = error.response.status;
          const responseData = error.response.data;
          
          if (status === 401) {
            errorMessage = 'API密钥无效';
          } else if (status === 403) {
            errorMessage = 'API密钥权限不足';
          } else if (status === 404) {
            errorMessage = 'API地址不正确';
          } else if (responseData && responseData.message) {
            errorMessage = responseData.message;
          } else {
            errorMessage = `HTTP ${status} 错误`;
          }
        } else if (error.code === 'NETWORK_ERROR') {
          errorMessage = '网络连接错误';
        }
        
        this.$message.error(errorMessage);
        
      } finally {
        this.testLoading = false;
      }
    },

    // 重置表单
    resetForm() {
      this.configForm.resetFields()
      this.currentType = ''
      this.modelOptions = []
      this.editingConfigId = null
      // 重置测试状态
      this.testStatus = ''
      this.testLoading = false
      this.testMessage = ''
    }
  }
}
</script>