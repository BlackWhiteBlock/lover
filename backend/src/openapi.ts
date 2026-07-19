const bearer = [{ bearerAuth: [] }];
const ok = { description: '成功' };
const notAuth = { description: '未认证' };
const notFound = { description: '资源不存在' };
const rateLimited = { description: '请求过于频繁' };
const secured = (summary: string, methods: string[] = ['get']) =>
  Object.fromEntries(methods.map((method) => [method, {
    summary, security: bearer,
    responses: { '200': ok, '401': notAuth, '404': notFound, '429': rateLimited },
  }]));
const jsonBody = (ref: string) => ({
  required: true,
  content: { 'application/json': { schema: { $ref: `#/components/schemas/${ref}` } } },
});
const jsonResp = (ref: string, desc = '成功') => ({
  [desc]: { content: { 'application/json': { schema: { $ref: `#/components/schemas/${ref}` } } } },
});

export const openApiDocument = {
  openapi: '3.0.3',
  info: {
    title: 'Lover MVP API',
    version: '0.2.0',
    description: '双人私密情侣空间后端。所有错误统一为 `{ error: { code, message, details? } }`。',
  },
  servers: [{ url: '/' }],
  tags: [
    { name: 'Auth' }, { name: 'Couples' }, { name: 'Media' },
    { name: 'Anniversaries' }, { name: 'Letters' },
    { name: 'Bootstrap' }, { name: 'Activity' }, { name: 'Quotes' },
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
      Ok: {
        type: 'object',
        required: ['ok'],
        properties: { ok: { type: 'boolean', example: true } },
      },
      TokenResponse: {
        type: 'object',
        required: ['accessToken', 'refreshToken', 'expiresIn'],
        properties: {
          accessToken: { type: 'string' },
          refreshToken: { type: 'string' },
          expiresIn: { type: 'integer', description: '秒' },
        },
      },
      User: {
        type: 'object',
        required: ['id', 'phone', 'nickname', 'createdAt'],
        properties: {
          id: { type: 'string', format: 'uuid' },
          phone: { type: 'string' },
          nickname: { type: 'string', maxLength: 30 },
          avatarAssetId: { type: 'string', format: 'uuid', nullable: true },
          avatarUrl: { type: 'string', format: 'uri', nullable: true },
          coupleCoverAssetId: { type: 'string', format: 'uuid', nullable: true },
          coupleCoverUrl: { type: 'string', format: 'uri', nullable: true },
          createdAt: { type: 'string', format: 'date-time' },
        },
      },
      MeResponse: {
        type: 'object',
        allOf: [
          { $ref: '#/components/schemas/User' },
          {
            type: 'object',
            properties: {
              activeSpaceId: { type: 'string', format: 'uuid', nullable: true },
              personalSpaceId: { type: 'string', format: 'uuid', nullable: true },
              loverSpaceId: { type: 'string', format: 'uuid', nullable: true },
              linked: { type: 'boolean' },
              coupleLinkId: { type: 'string', format: 'uuid', nullable: true },
              profileCompleted: { type: 'boolean' },
            },
          },
        ],
      },
      CoupleMember: {
        type: 'object',
        required: ['id', 'userId', 'nickname'],
        properties: {
          id: { type: 'string', format: 'uuid' },
          userId: { type: 'string', format: 'uuid' },
          nickname: { type: 'string' },
          avatarAssetId: { type: 'string', format: 'uuid', nullable: true },
          avatarUrl: { type: 'string', format: 'uri', nullable: true },
        },
      },
      CoupleSpace: {
        type: 'object',
        properties: {
          id: { type: 'string', format: 'uuid' },
          name: { type: 'string' },
          togetherDate: { type: 'string', format: 'date', nullable: true },
          lovingDays: { type: 'integer' },
          members: { type: 'array', items: { $ref: '#/components/schemas/CoupleMember' } },
          pendingUnbinding: { type: 'boolean' },
          pendingIncomingBinds: { type: 'array', items: { type: 'object', properties: { id: { type: 'string' } } } },
          pendingOutgoingBind: { type: 'object', nullable: true },
        },
      },
      Countdown: {
        type: 'object',
        required: ['days', 'reached'],
        properties: {
          days: { type: 'integer' },
          nextDate: { type: 'string', format: 'date', nullable: true },
          reached: { type: 'boolean' },
        },
      },
      Anniversary: {
        type: 'object',
        required: ['id', 'title', 'date', 'type', 'createdAt'],
        properties: {
          id: { type: 'string', format: 'uuid' },
          title: { type: 'string', maxLength: 30 },
          date: { type: 'string', format: 'date' },
          type: { type: 'string', enum: ['yearly', 'milestone'] },
          coverAssetId: { type: 'string', format: 'uuid', nullable: true },
          createdBy: { type: 'string', format: 'uuid' },
          createdAt: { type: 'string', format: 'date-time' },
          countdown: { $ref: '#/components/schemas/Countdown' },
        },
      },
      Letter: {
        type: 'object',
        required: ['id', 'senderId', 'senderNickname', 'title', 'type', 'isUnlocked', 'createdAt'],
        properties: {
          id: { type: 'string', format: 'uuid' },
          senderId: { type: 'string', format: 'uuid' },
          senderNickname: { type: 'string' },
          title: { type: 'string', maxLength: 40 },
          content: { type: 'string', nullable: true, description: '未解锁时为 null' },
          summary: { type: 'string', nullable: true },
          type: { type: 'string', enum: ['instant', 'capsule'] },
          unlockAt: { type: 'string', format: 'date-time', nullable: true },
          unlockOnPartnerBind: { type: 'boolean' },
          isUnlocked: { type: 'boolean' },
          createdAt: { type: 'string', format: 'date-time' },
        },
      },
      MediaAssetPart: {
        type: 'object',
        required: ['id', 'assetId', 'type', 'sortOrder'],
        properties: {
          id: { type: 'string', format: 'uuid' },
          assetId: { type: 'string', format: 'uuid' },
          thumbnailAssetId: { type: 'string', format: 'uuid', nullable: true },
          type: { type: 'string', enum: ['image', 'video'] },
          url: { type: 'string', format: 'uri' },
          thumbnailUrl: { type: 'string', format: 'uri', nullable: true },
          sortOrder: { type: 'integer' },
        },
      },
      MediaItem: {
        type: 'object',
        required: ['id', 'caption', 'mediaDate', 'uploaderId', 'assets', 'createdAt'],
        properties: {
          id: { type: 'string', format: 'uuid' },
          caption: { type: 'string' },
          mediaDate: { type: 'string', format: 'date' },
          uploaderId: { type: 'string', format: 'uuid' },
          assets: { type: 'array', items: { $ref: '#/components/schemas/MediaAssetPart' } },
          createdAt: { type: 'string', format: 'date-time' },
        },
      },
      ItemPage: {
        type: 'object',
        required: ['items'],
        properties: {
          items: { type: 'array' },
          nextCursor: { type: 'string', nullable: true, description: 'cursor 分页令牌，null 表示无更多数据' },
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
      PatchMeRequest: {
        type: 'object', additionalProperties: false,
        properties: {
          nickname: { type: 'string', maxLength: 30 },
          avatarAssetId: { type: 'string', format: 'uuid', nullable: true },
          coupleCoverAssetId: { type: 'string', format: 'uuid', nullable: true },
        },
      },
      UpdateCoupleSpaceRequest: {
        type: 'object', additionalProperties: false,
        properties: {
          name: { type: 'string' },
          togetherDate: { type: 'string', format: 'date', nullable: true },
        },
      },
      CreateAnniversaryRequest: {
        type: 'object', additionalProperties: false,
        required: ['title', 'date', 'type'],
        properties: {
          title: { type: 'string', maxLength: 30 },
          date: { type: 'string', format: 'date' },
          type: { type: 'string', enum: ['yearly', 'milestone'] },
          coverAssetId: { type: 'string', format: 'uuid', nullable: true },
        },
      },
      UpdateAnniversaryRequest: {
        type: 'object', additionalProperties: false,
        description: '部分更新，至少提供一个字段',
        properties: {
          title: { type: 'string', maxLength: 30 },
          date: { type: 'string', format: 'date' },
          type: { type: 'string', enum: ['yearly', 'milestone'] },
          coverAssetId: { type: 'string', format: 'uuid', nullable: true },
        },
      },
      CreateLetterRequest: {
        type: 'object', additionalProperties: false,
        required: ['title', 'content', 'type'],
        properties: {
          title: { type: 'string', maxLength: 40 },
          content: { type: 'string' },
          type: { type: 'string', enum: ['instant', 'capsule'] },
          unlockAt: { type: 'string', format: 'date-time', nullable: true },
          unlockOnPartnerBind: { type: 'boolean' },
        },
      },
      CreateMediaRequest: {
        type: 'object', additionalProperties: false,
        required: ['caption', 'assets'],
        properties: {
          caption: { type: 'string' },
          mediaDate: { type: 'string', format: 'date' },
          assets: {
            type: 'array',
            items: {
              type: 'object',
              required: ['assetId', 'type'],
              properties: {
                assetId: { type: 'string', format: 'uuid' },
                type: { type: 'string', enum: ['image', 'video'] },
                thumbnailAssetId: { type: 'string', format: 'uuid', nullable: true },
              },
            },
          },
        },
      },
      UpdateMediaRequest: {
        type: 'object', additionalProperties: false,
        description: '部分更新',
        properties: {
          caption: { type: 'string' },
          mediaDate: { type: 'string', format: 'date' },
          addAssets: {
            type: 'array',
            items: {
              type: 'object',
              required: ['assetId', 'type'],
              properties: {
                assetId: { type: 'string', format: 'uuid' },
                type: { type: 'string', enum: ['image', 'video'] },
                thumbnailAssetId: { type: 'string', format: 'uuid', nullable: true },
              },
            },
          },
          removeAssetIds: { type: 'array', items: { type: 'string', format: 'uuid' } },
          assetOrder: { type: 'array', items: { type: 'string', format: 'uuid' } },
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
      SignResponse: {
        type: 'object',
        required: ['url', 'expiresIn'],
        properties: {
          url: { type: 'string', format: 'uri' },
          expiresIn: { type: 'integer' },
        },
      },
      CompleteUploadRequest: {
        type: 'object',
        additionalProperties: false,
        required: ['assetId'],
        description: '仅接收 assetId；objectKey、owner、provider、bucket、大小和 MIME 均由服务端数据库及 provider stat 确认。',
        properties: { assetId: { type: 'string', format: 'uuid' } },
      },
      DailyQuote: {
        type: 'object',
        properties: {
          text: { type: 'string' },
          audience: { type: 'string', enum: ['couple', 'solo'] },
        },
      },
      PartnerActivity: {
        type: 'object',
        properties: {
          id: { type: 'string', format: 'uuid' },
          type: { type: 'string', enum: ['letter_instant', 'letter_capsule', 'media_created', 'anniversary_created'] },
          payload: { type: 'object' },
          read: { type: 'boolean' },
          createdAt: { type: 'string', format: 'date-time' },
        },
      },
    },
  },
  paths: {
    '/health': { get: { summary: '服务与数据库健康检查', responses: { '200': ok, '503': { description: '数据库不可用' } } } },
    '/ready': { get: { summary: '数据库就绪检查', responses: { '200': ok, '503': { description: '未就绪' } } } },
    '/api/auth/sms/send': {
      post: {
        tags: ['Auth'], summary: '发送短信验证码',
        requestBody: jsonBody('SmsSend'),
        responses: { '200': ok, '429': rateLimited },
      },
    },
    '/api/auth/sms/login': {
      post: {
        tags: ['Auth'], summary: '验证码登录/注册（新登录会作废旧会话）',
        requestBody: jsonBody('SmsLogin'),
        responses: { '200': jsonResp('TokenResponse', '登录成功'), '201': jsonResp('TokenResponse', '注册成功') },
      },
    },
    '/api/auth/pnvs/sdk-info': {
      get: {
        tags: ['Auth'],
        summary: '下发号码认证方案密钥与协议链接（登录前）',
        parameters: [{
          name: 'platform', in: 'query', required: true,
          schema: { type: 'string', enum: ['android', 'harmony'] },
        }],
        responses: { '200': ok, '429': rateLimited },
      },
    },
    '/api/auth/pnvs/login': {
      post: {
        tags: ['Auth'],
        summary: '本机号一键登录/注册（服务端 GetMobile）',
        responses: { '200': jsonResp('TokenResponse', '登录成功'), '201': jsonResp('TokenResponse', '注册成功') },
      },
    },
    '/legal/privacy': {
      get: { tags: ['Legal'], summary: '隐私政策占位页', responses: { '200': { description: 'HTML' } } },
    },
    '/legal/terms': {
      get: { tags: ['Legal'], summary: '用户协议占位页', responses: { '200': { description: 'HTML' } } },
    },
    '/api/auth/refresh': {
      post: {
        tags: ['Auth'], summary: '轮换刷新令牌',
        responses: { '200': jsonResp('TokenResponse') },
      },
    },
    '/api/auth/logout': secured('注销当前会话', ['post']),
    '/api/me': {
      get: {
        tags: ['Auth'], summary: '当前用户信息',
        security: bearer,
        responses: { '200': jsonResp('MeResponse'), '401': notAuth },
      },
      patch: {
        tags: ['Auth'], summary: '更新昵称/头像/情侣合照',
        security: bearer,
        requestBody: jsonBody('PatchMeRequest'),
        responses: { '200': jsonResp('MeResponse'), '401': notAuth },
      },
    },
    '/api/users/lookup': secured('按手机号预览用户（绑定前）'),
    '/api/onboarding': {
      post: {
        tags: ['Couples'], summary: '创建个人空间（首次登录）',
        security: bearer,
        responses: { '201': ok },
      },
    },
    '/api/couple-invites': {
      post: {
        tags: ['Couples'], summary: '创建绑定邀请（返回 code + inviteUrl）',
        security: bearer,
        responses: { '201': ok },
      },
    },
    '/api/couple-invites/accept': secured('接受绑定邀请', ['post']),
    '/api/couple-invites/{id}': secured('取消邀请', ['delete']),
    '/api/couple-binds/{id}/reject': secured('拒绝绑定邀请', ['post']),
    '/api/couple-binds/{id}/cancel': secured('取消绑定邀请', ['post']),
    '/invite/{code}': {
      get: {
        tags: ['Couples'],
        summary: '邀请 H5 落地页（打开 App / 复制邀请码）',
        parameters: [{ name: 'code', in: 'path', required: true, schema: { type: 'string' } }],
        responses: { '200': { description: 'text/html' } },
      },
    },
    '/api/couple-space': {
      get: {
        tags: ['Couples'], summary: '情侣空间详情',
        security: bearer,
        responses: { '200': jsonResp('CoupleSpace'), '401': notAuth },
      },
      patch: {
        tags: ['Couples'], summary: '更新情侣空间（名称/在一起日期）',
        security: bearer,
        requestBody: jsonBody('UpdateCoupleSpaceRequest'),
        responses: { '200': jsonResp('CoupleSpace'), '401': notAuth },
      },
    },
    '/api/couple-link': {
      patch: {
        tags: ['Couples'], summary: '更新情侣链接（名称/在一起日期）',
        security: bearer,
        responses: { '200': ok },
      },
    },
    '/api/couple-space/unbinding': secured('申请解绑', ['post']),
    '/api/couple-space/unbinding/{id}/confirm': secured('伴侣确认解绑', ['post']),
    '/api/couple-space/unbinding/{id}/cancel': secured('取消解绑', ['post']),
    '/api/bootstrap': {
      get: {
        tags: ['Bootstrap'], summary: '首页聚合数据（空间+成员+最近媒体+每日寄语+相爱天数）',
        security: bearer,
        responses: { '200': ok, '401': notAuth },
      },
    },
    '/api/daily-quote': {
      get: {
        tags: ['Quotes'], summary: '今日寄语（按日历日轮换）',
        security: bearer,
        responses: { '200': jsonResp('DailyQuote') },
      },
    },
    '/api/media-assets/token': {
      post: {
        tags: ['Media'],
        summary: '申请 local/七牛上传令牌',
        security: bearer,
        requestBody: jsonBody('UploadTokenRequest'),
        responses: {
          '201': jsonResp('UploadGrant', '上传授权'),
          '429': rateLimited,
        },
      },
    },
    '/api/media-assets/local-upload': {
      post: {
        tags: ['Media'],
        summary: '开发环境 local multipart 上传',
        security: bearer,
        responses: { '200': ok, '404': notFound },
      },
    },
    '/api/media-assets/complete': {
      post: {
        tags: ['Media'],
        summary: '按 DB 精确归属及 provider stat 大小/MIME 验证完成资产',
        security: bearer,
        requestBody: jsonBody('CompleteUploadRequest'),
        responses: { '200': ok, '404': notFound },
      },
    },
    '/api/media-assets/{assetId}/sign': {
      post: {
        tags: ['Media'],
        summary: '签发 original/thumb 私有下载 URL',
        security: bearer,
        requestBody: jsonBody('SignRequest'),
        responses: { '200': jsonResp('SignResponse') },
      },
    },
    '/api/media': {
      get: {
        tags: ['Media'], summary: '媒体列表（cursor 分页）',
        security: bearer,
        parameters: [
          { name: 'cursor', in: 'query', schema: { type: 'string' }, description: '分页游标' },
          { name: 'limit', in: 'query', schema: { type: 'integer', default: 30, maximum: 100 } },
        ],
        responses: { '200': ok, '401': notAuth },
      },
      post: {
        tags: ['Media'], summary: '创建媒体项（最多 20 个资产）',
        security: bearer,
        requestBody: jsonBody('CreateMediaRequest'),
        responses: { '201': ok },
      },
    },
    '/api/media/{id}': {
      get: {
        tags: ['Media'], summary: '媒体详情',
        security: bearer,
        parameters: [{ name: 'id', in: 'path', required: true, schema: { type: 'string', format: 'uuid' } }],
        responses: { '200': jsonResp('MediaItem'), '404': notFound },
      },
      patch: {
        tags: ['Media'], summary: '更新媒体项（标题/日期/资产增删重排）',
        security: bearer,
        parameters: [{ name: 'id', in: 'path', required: true, schema: { type: 'string', format: 'uuid' } }],
        requestBody: jsonBody('UpdateMediaRequest'),
        responses: { '200': jsonResp('MediaItem'), '404': notFound },
      },
      delete: {
        tags: ['Media'], summary: '删除媒体项',
        security: bearer,
        parameters: [{ name: 'id', in: 'path', required: true, schema: { type: 'string', format: 'uuid' } }],
        responses: { '200': jsonResp('Ok'), '404': notFound },
      },
    },
    '/api/anniversaries': {
      get: {
        tags: ['Anniversaries'], summary: '纪念日列表',
        security: bearer,
        responses: { '200': ok, '401': notAuth },
      },
      post: {
        tags: ['Anniversaries'], summary: '创建纪念日',
        security: bearer,
        requestBody: jsonBody('CreateAnniversaryRequest'),
        responses: { '201': jsonResp('Anniversary') },
      },
    },
    '/api/anniversaries/{id}': {
      get: {
        tags: ['Anniversaries'], summary: '纪念日详情',
        security: bearer,
        parameters: [{ name: 'id', in: 'path', required: true, schema: { type: 'string', format: 'uuid' } }],
        responses: { '200': jsonResp('Anniversary'), '404': notFound },
      },
      patch: {
        tags: ['Anniversaries'], summary: '更新纪念日',
        security: bearer,
        parameters: [{ name: 'id', in: 'path', required: true, schema: { type: 'string', format: 'uuid' } }],
        requestBody: jsonBody('UpdateAnniversaryRequest'),
        responses: { '200': jsonResp('Anniversary'), '404': notFound },
      },
      delete: {
        tags: ['Anniversaries'], summary: '删除纪念日（仅创建者可删）',
        security: bearer,
        parameters: [{ name: 'id', in: 'path', required: true, schema: { type: 'string', format: 'uuid' } }],
        responses: { '200': jsonResp('Ok'), '404': notFound },
      },
    },
    '/api/letters': {
      get: {
        tags: ['Letters'], summary: '信件列表',
        security: bearer,
        responses: { '200': ok, '401': notAuth },
      },
      post: {
        tags: ['Letters'], summary: '创建信件（即时信需已绑定）',
        security: bearer,
        requestBody: jsonBody('CreateLetterRequest'),
        responses: { '201': jsonResp('Letter') },
      },
    },
    '/api/letters/{id}': {
      get: {
        tags: ['Letters'], summary: '信件详情',
        security: bearer,
        parameters: [{ name: 'id', in: 'path', required: true, schema: { type: 'string', format: 'uuid' } }],
        responses: { '200': jsonResp('Letter'), '404': notFound },
      },
      put: {
        tags: ['Letters'], summary: '全量替换信件（仅作者）',
        security: bearer,
        parameters: [{ name: 'id', in: 'path', required: true, schema: { type: 'string', format: 'uuid' } }],
        requestBody: jsonBody('CreateLetterRequest'),
        responses: { '200': jsonResp('Letter'), '403': { description: '仅作者可编辑' } },
      },
      delete: {
        tags: ['Letters'], summary: '删除信件（仅作者）',
        security: bearer,
        parameters: [{ name: 'id', in: 'path', required: true, schema: { type: 'string', format: 'uuid' } }],
        responses: { '200': jsonResp('Ok'), '403': { description: '仅作者可删除' } },
      },
    },
    '/api/activity': {
      get: {
        tags: ['Activity'], summary: '伴侣活动通知列表',
        security: bearer,
        parameters: [
          { name: 'unreadOnly', in: 'query', schema: { type: 'boolean', default: true } },
          { name: 'limit', in: 'query', schema: { type: 'integer', default: 20 } },
        ],
        responses: { '200': ok },
      },
    },
    '/api/activity/read': {
      post: {
        tags: ['Activity'], summary: '标记活动已读',
        security: bearer,
        responses: { '200': ok },
      },
    },
  },
} as const;
