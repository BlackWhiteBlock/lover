const bearer = [{ bearerAuth: [] }];
const ok = { description: '成功' };
const secured = (summary: string, methods: string[] = ['get']) =>
  Object.fromEntries(methods.map((method) => [method, { summary, security: bearer, responses: { '200': ok } }]));

export const openApiDocument = {
  openapi: '3.0.3',
  info: {
    title: 'Lover MVP API',
    version: '0.1.0',
    description: '双人私密情侣空间后端。所有错误统一为 `{ error: { code, message, details? } }`。',
  },
  servers: [{ url: '/' }],
  tags: [
    { name: 'Auth' }, { name: 'Couples' }, { name: 'Media' },
    { name: 'Anniversaries' }, { name: 'Letters' },
  ],
  components: {
    securitySchemes: {
      bearerAuth: { type: 'http', scheme: 'bearer', bearerFormat: 'JWT' },
    },
    schemas: {
      Error: {
        type: 'object',
        required: ['error'],
        properties: {
          error: {
            type: 'object',
            required: ['code', 'message'],
            properties: {
              code: { type: 'string' },
              message: { type: 'string' },
              details: {},
            },
          },
        },
      },
      SmsSend: {
        type: 'object', additionalProperties: false, required: ['phone'],
        properties: { phone: { type: 'string', example: '13800138000' } },
      },
      SmsLogin: {
        type: 'object', additionalProperties: false, required: ['phone', 'code'],
        properties: {
          phone: { type: 'string' },
          code: { type: 'string', pattern: '^\\d{6}$' },
          nickname: { type: 'string', maxLength: 30 },
        },
      },
      UploadTokenRequest: {
        type: 'object',
        additionalProperties: false,
        required: ['fileName', 'mimeType', 'sizeBytes'],
        properties: {
          fileName: { type: 'string', maxLength: 200 },
          mimeType: {
            type: 'string',
            enum: ['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'video/mp4', 'video/quicktime'],
          },
          sizeBytes: { type: 'integer', minimum: 1, maximum: 314572800 },
        },
      },
      UploadGrant: {
        type: 'object',
        required: ['assetId', 'provider', 'uploadToken', 'uploadUrl', 'objectKey', 'expiresIn'],
        properties: {
          assetId: { type: 'string', format: 'uuid' },
          provider: { type: 'string', enum: ['local', 'qiniu'] },
          uploadToken: { type: 'string' },
          uploadUrl: { type: 'string', format: 'uri' },
          objectKey: { type: 'string' },
          expiresIn: { type: 'integer' },
          uploadFields: { type: 'object', additionalProperties: { type: 'string' } },
        },
      },
      SignRequest: {
        type: 'object',
        additionalProperties: false,
        properties: { variant: { type: 'string', enum: ['original', 'thumb'], default: 'original' } },
      },
      CompleteUploadRequest: {
        type: 'object',
        additionalProperties: false,
        required: ['assetId'],
        description: '仅接收 assetId；objectKey、owner、provider、bucket、大小和 MIME 均由服务端数据库及 provider stat 确认。',
        properties: { assetId: { type: 'string', format: 'uuid' } },
      },
    },
  },
  paths: {
    '/health': { get: { summary: '服务与数据库健康检查', responses: { '200': ok, '503': { description: '数据库不可用' } } } },
    '/ready': { get: { summary: '数据库就绪检查', responses: { '200': ok, '503': { description: '未就绪' } } } },
    '/api/auth/sms/send': { post: { tags: ['Auth'], summary: '发送短信验证码', responses: { '200': ok } } },
    '/api/auth/sms/login': { post: { tags: ['Auth'], summary: '验证码登录/注册', responses: { '200': ok, '201': ok } } },
    '/api/auth/refresh': { post: { tags: ['Auth'], summary: '轮换刷新令牌', responses: { '200': ok } } },
    '/api/auth/logout': secured('注销当前会话', ['post']),
    '/api/me': secured('当前用户 / 更新头像资产', ['get', 'patch']),
    '/api/bootstrap': secured('首页 bootstrap'),
    '/api/couple-space': secured('情侣空间详情/更新', ['get', 'patch']),
    '/api/couple-invites': secured('创建邀请（返回 code + inviteUrl；新建空间须带 togetherDate）', ['post']),
    '/api/couple-invites/accept': secured('接受邀请', ['post']),
    '/api/couple-invites/{id}': secured('取消邀请', ['delete']),
    '/invite/{code}': {
      get: {
        tags: ['Couple'],
        summary: '邀请 H5 落地页（打开 App / 复制邀请码）',
        parameters: [{ name: 'code', in: 'path', required: true, schema: { type: 'string' } }],
        responses: { '200': { description: 'text/html' } },
      },
    },
    '/api/couple-space/unbinding': secured('申请解绑', ['post']),
    '/api/couple-space/unbinding/{id}/confirm': secured('伴侣确认解绑', ['post']),
    '/api/couple-space/unbinding/{id}/cancel': secured('取消解绑', ['post']),
    '/api/media-assets/token': {
      post: {
        summary: '申请 local/七牛上传令牌',
        security: bearer,
        requestBody: {
          required: true,
          content: { 'application/json': { schema: { $ref: '#/components/schemas/UploadTokenRequest' } } },
        },
        responses: {
          '201': { description: '上传授权', content: { 'application/json': { schema: { $ref: '#/components/schemas/UploadGrant' } } } },
          '429': { description: '请求过于频繁' },
        },
      },
    },
    '/api/media-assets/local-upload': { post: { summary: '开发环境 local multipart 上传', security: bearer, responses: { '200': ok, '404': { description: 'local provider 未启用' } } } },
    '/api/media-assets/complete': {
      post: {
        summary: '按 DB 精确归属及 provider stat 大小/MIME 验证完成资产',
        security: bearer,
        requestBody: {
          required: true,
          content: { 'application/json': { schema: { $ref: '#/components/schemas/CompleteUploadRequest' } } },
        },
        responses: { '200': ok, '404': { description: '资产不存在或不属于当前用户和空间' } },
      },
    },
    '/api/media-assets/{assetId}/sign': {
      post: {
        summary: '签发 original/thumb 私有下载 URL',
        security: bearer,
        requestBody: {
          content: { 'application/json': { schema: { $ref: '#/components/schemas/SignRequest' } } },
        },
        responses: { '200': ok },
      },
    },
    '/api/media': secured('媒体列表/创建', ['get', 'post']),
    '/api/media/{id}': secured('媒体详情/更新/删除', ['get', 'patch', 'delete']),
    '/api/anniversaries': secured('纪念日列表/创建', ['get', 'post']),
    '/api/anniversaries/{id}': secured('纪念日详情/更新/删除', ['get', 'patch', 'delete']),
    '/api/letters': secured('信件列表/创建', ['get', 'post']),
    '/api/letters/{id}': secured('信件详情/替换/删除', ['get', 'put', 'delete']),
  },
} as const;
