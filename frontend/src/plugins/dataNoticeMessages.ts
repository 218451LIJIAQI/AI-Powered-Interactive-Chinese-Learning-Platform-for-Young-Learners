/** Default titles / messages of the shared DataNotice banner (previously hard-coded English). */
export const dataNoticeMessages = {
  en: {
    errorTitle: 'Connection notice',
    successTitle: 'Saved successfully',
    fallbackTitle: 'Preview data mode',
    fallbackMessage: 'Showing safe preview data. Connect the backend to use live platform data.',
    infoTitle: 'Good to know',
    warningTitle: 'Please check',
  },
  ms: {
    errorTitle: 'Notis sambungan',
    successTitle: 'Berjaya disimpan',
    fallbackTitle: 'Mod data pratonton',
    fallbackMessage:
      'Memaparkan data pratonton yang selamat. Sambungkan pelayan untuk menggunakan data platform sebenar.',
    infoTitle: 'Untuk makluman',
    warningTitle: 'Sila semak',
  },
  'zh-CN': {
    errorTitle: '连接提示',
    successTitle: '保存成功',
    fallbackTitle: '预览数据模式',
    fallbackMessage: '正在显示安全的预览数据。连接后端后即可使用真实平台数据。',
    infoTitle: '温馨提示',
    warningTitle: '请检查',
  },
} as const
