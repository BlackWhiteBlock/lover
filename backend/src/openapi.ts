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
    },
  },
  paths: {
    '/health': { get: { summary: '存活检查', responses: { '200': ok } } },
    '/ready': { get: { summary: '数据库就绪检查', responses: { '200': ok, '503': { description: '未就绪' } } } },
    '/api/auth/sms/send': { post: { tags: ['Auth'], summary: '发送短信验证码', responses: { '200': ok } } },
    '/api/auth/sms/login': { post: { tags: ['Auth'], summary: '验证码登录/注册', responses: { '200': ok, '201': ok } } },
    '/api/auth/refresh': { post: { tags: ['Auth'], summary: '轮换刷新令牌', responses: { '200': ok } } },
    '/api/auth/logout': secured('注销当前会话', ['post']),
    '/api/me': secured('当前用户'),
    '/api/bootstrap': secured('首页 bootstrap'),
    '/api/couple-space': secured('情侣空间详情/更新', ['get', 'patch']),
    '/api/couple-invites': secured('创建邀请', ['post']),
    '/api/couple-invites/accept': secured('接受邀请', ['post']),
    '/api/couple-invites/{id}': secured('取消邀请', ['delete']),
    '/api/couple-space/unbinding': secured('申请解绑', ['post']),
    '/api/couple-space/unbinding/{id}/confirm': secured('伴侣确认解绑', ['post']),
    '/api/couple-space/unbinding/{id}/cancel': secured('取消解绑', ['post']),
    '/api/media-assets/token': secured('申请本地上传令牌', ['post']),
    '/api/media-assets/local-upload': { post: { summary: '持上传令牌上传文件', security: bearer, responses: { '200': ok } } },
    '/api/media-assets/complete': secured('确认资产上传完成', ['post']),
    '/api/media-assets/{assetId}/sign': secured('签发私有下载 URL', ['post']),
    '/api/media': secured('媒体列表/创建', ['get', 'post']),
    '/api/media/{id}': secured('媒体详情/更新/删除', ['get', 'patch', 'delete']),
    '/api/anniversaries': secured('纪念日列表/创建', ['get', 'post']),
    '/api/anniversaries/{id}': secured('纪念日详情/更新/删除', ['get', 'patch', 'delete']),
    '/api/letters': secured('信件列表/创建', ['get', 'post']),
    '/api/letters/{id}': secured('信件详情/替换/删除', ['get', 'put', 'delete']),
  },
} as const;
