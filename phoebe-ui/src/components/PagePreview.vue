<template>
  <!--
    中间页（企业商业专属问答页）预览示意。布局依据公开资料：
    PC：上方 banner、右侧企业信息（Logo/咨询按钮/二维码）、下方产品介绍区；问答以“企业回答”形式展示（资料 A 截图、C）。
    无线：顶部 Logo/联系商家/APP 下载，底部产品图与产品文字链（资料 C）。具体像素与视觉为示意。
  -->
  <div v-if="content" :class="['mp', device === 'MOBILE' ? 'mobile' : 'pc']">
    <template v-if="device !== 'MOBILE'">
      <div class="banner" v-if="content.banners?.length">
        <img :src="content.banners[0].image" @click="emit('inner', 'banner')" />
        <span class="ad">广告</span>
      </div>
      <div class="body">
        <div class="main">
          <div class="q"><span class="qi">?</span>{{ question || '问题标题' }}</div>
          <div class="ans">
            <div class="ans-h"><el-icon><UserFilled /></el-icon> 企业回答 <span class="company">{{ content.companyInfo?.name }}</span></div>
            <div class="answer-html" v-html="answer || '<p>企业回答内容</p>'" />
          </div>
          <div :class="['products', pcStyle === 'TEXT_LINK' ? 'links' : '']">
            <div class="product" v-for="(p, i) in content.products" :key="'p' + i" @click="emit('inner', 'product')">
              <img v-if="p.image && pcStyle !== 'TEXT_LINK'" :src="p.image" />
              <div><a>{{ p.title }}</a><div class="muted">{{ p.desc }}</div></div>
            </div>
          </div>
        </div>
        <div class="side">
          <img v-if="content.companyInfo?.logo" :src="content.companyInfo.logo" class="logo" />
          <div class="cname">{{ content.companyInfo?.name }}</div>
          <p class="intro">{{ content.companyInfo?.intro }}</p>
          <span class="btn" @click="emit('inner', 'consult')">咨 询</span>
          <div v-if="content.companyInfo?.phone" class="phone">☎ {{ content.companyInfo.phone }}</div>
          <img v-if="content.companyInfo?.qrCode" :src="content.companyInfo.qrCode" class="qr" @click="emit('inner', 'qrcode')" />
          <a class="site" @click="emit('inner', 'website')">访问官网 ›</a>
        </div>
      </div>
    </template>
    <template v-else>
      <div class="m-top">
        <img v-if="content.companyInfo?.logo" :src="content.companyInfo.logo" class="logo" />
        <b>{{ content.companyInfo?.name }}</b>
        <span class="spacer" />
        <span v-if="content.appDownloadUrl" class="m-btn ghost" @click="emit('inner', 'app')">APP下载</span>
        <span class="m-btn" @click="emit('inner', 'consult')">联系商家</span>
      </div>
      <img v-for="(b, i) in content.banners" :key="'mb' + i" :src="b.image" class="m-banner" @click="emit('inner', 'banner')" />
      <div class="m-q"><span class="qi">?</span>{{ question || '问题标题' }}</div>
      <div class="m-ans">
        <div class="ans-h">企业回答</div>
        <div class="answer-html" v-html="answer || '<p>企业回答内容</p>'" />
      </div>
      <div class="m-products">
        <div class="m-product" v-for="(p, i) in content.products" :key="'mp' + i" @click="emit('inner', 'product')">
          <img v-if="p.image" :src="p.image" /><div>{{ p.title }}</div>
        </div>
      </div>
      <div class="m-foot">
        <span class="m-btn block" @click="emit('inner', 'consult')">免费咨询</span>
        <span v-if="content.companyInfo?.phone" class="m-btn block ghost" @click="emit('inner', 'phone')">电话咨询</span>
      </div>
    </template>
  </div>
  <el-empty v-else description="暂无内容" />
</template>

<script setup>
defineProps({ content: Object, device: { type: String, default: 'PC' }, pcStyle: String, question: String, answer: String })
const emit = defineEmits(['inner'])
</script>

<style scoped>
.mp { background: #fff; font-size: 13px; color: #333; border: 1px solid #e6e8eb; }
.banner { position: relative; }
.banner img { width: 100%; display: block; max-height: 120px; object-fit: cover; cursor: pointer; }
.ad { position: absolute; right: 4px; bottom: 4px; font-size: 10px; color: #fff; background: rgba(0,0,0,.35); padding: 0 4px; }
.body { display: flex; gap: 16px; padding: 14px 16px; }
.main { flex: 1; min-width: 0; }
.q { font-size: 17px; font-weight: 600; margin-bottom: 10px; display: flex; gap: 8px; align-items: flex-start; }
.qi { flex-shrink: 0; width: 20px; height: 20px; border-radius: 50%; border: 2px solid #999; color: #999; font-size: 13px; display: inline-flex; align-items: center; justify-content: center; margin-top: 2px; }
.ans { border-top: 2px solid #2bb24c; background: #f6fbf7; padding: 10px 12px; margin-bottom: 12px; }
.ans-h { color: #2bb24c; font-size: 15px; font-weight: 600; display: flex; align-items: center; gap: 4px; margin-bottom: 6px; }
.company { color: #999; font-size: 12px; font-weight: normal; margin-left: 6px; }
.products { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; border: 1px solid #ebeef5; padding: 10px; }
.products.links { grid-template-columns: 1fr; gap: 4px; }
.product { display: flex; gap: 8px; cursor: pointer; }
.product img { width: 72px; height: 72px; object-fit: cover; }
.side { width: 190px; border: 1px solid #ebeef5; padding: 12px; text-align: center; align-self: flex-start; }
.logo { width: 64px; height: 64px; border-radius: 4px; }
.cname { font-weight: 600; margin: 6px 0 4px; }
.intro { font-size: 12px; color: #999; margin: 0 0 8px; line-height: 1.5; }
.btn { display: inline-block; background: #2bb24c; color: #fff; padding: 4px 26px; border-radius: 2px; cursor: pointer; }
.phone { margin-top: 6px; font-size: 12px; color: #666; }
.qr { width: 100px; margin-top: 8px; cursor: pointer; }
.site { display: block; margin-top: 6px; font-size: 12px; cursor: pointer; }
.mobile { width: 360px; margin: 0 auto; border-radius: 10px; overflow: hidden; background: #f5f5f5; }
.m-top { display: flex; align-items: center; gap: 8px; padding: 8px 10px; background: #fff; }
.m-top .logo { width: 32px; height: 32px; }
.spacer { flex: 1; }
.m-btn { background: #2bb24c; color: #fff; font-size: 12px; padding: 3px 10px; border-radius: 2px; cursor: pointer; text-align: center; }
.m-btn.ghost { background: #fff; color: #2bb24c; border: 1px solid #2bb24c; }
.m-btn.block { flex: 1; padding: 8px; font-size: 14px; }
.m-banner { width: 100%; display: block; }
.m-q { background: #fff; padding: 12px; font-size: 16px; font-weight: 600; display: flex; gap: 6px; margin-top: 6px; }
.m-ans { background: #fff; padding: 0 12px 12px; }
.m-ans .ans-h { font-size: 14px; }
.m-products { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; padding: 10px; background: #fff; margin-top: 6px; }
.m-product img { width: 100%; height: 100px; object-fit: cover; display: block; }
.m-product div { font-size: 13px; color: #3385ff; margin-top: 4px; }
.m-foot { display: flex; gap: 8px; padding: 10px; background: #fff; border-top: 1px solid #eee; }
</style>
