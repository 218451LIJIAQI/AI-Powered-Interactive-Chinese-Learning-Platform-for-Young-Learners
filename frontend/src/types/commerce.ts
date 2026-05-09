export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'CANCELLED' | 'REFUNDED'
export type RefundStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'REFUNDED'
export type PayoutStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'PAID'
export type PurchasableItemType = 'PACKAGE' | 'COURSE' | 'MODULE' | 'PREMIUM_AI' | 'SUBSCRIPTION' | 'LESSON'

export type CartItem = {
  cartItemId: number
  itemType: PurchasableItemType | string
  itemId: number
  title: string
  unitPrice: number
  currencyCode: string
  quantity: number
  subtotal: number
  teacherOwned: boolean
}

export type Cart = {
  cartId: number
  items: CartItem[]
  totalAmount: number
  currencyCode: string
  empty: boolean
}

export type CheckoutResponse = {
  orderId: number
  orderNumber: string
  paymentId: number
  paymentStatus: PaymentStatus
  totalAmount: number
  currencyCode: string
  checkoutUrl: string | null
  providerName: string
  providerStatus: string
  paymentAvailable: boolean
  message: string
  items: CartItem[]
}

export type PaymentRecord = {
  paymentId: number
  orderId: number | null
  orderNumber: string | null
  studentId: number | null
  studentName: string | null
  studentEmail: string | null
  providerName: string
  providerReference: string | null
  amount: number
  currencyCode: string
  paymentStatus: PaymentStatus
  paymentMethod: string | null
  providerStatus: string | null
  paidAt: string | null
  createdAt: string
  items: CartItem[]
}

export type SubscriptionPlan = {
  id: number
  name: string
  price: number
  currencyCode: string
  benefits: string
  active: boolean
}

export type StudentSubscription = {
  id: number
  plan: SubscriptionPlan
  subscriptionStatus: PaymentStatus
  startsAt: string | null
  endsAt: string | null
  paymentId: number | null
}

export type TeacherEarning = {
  id: number
  orderItemId: number | null
  itemTitle: string | null
  grossAmount: number
  commissionAmount: number
  netAmount: number
  earningStatus: string
  createdAt: string
}

export type TeacherEarningSummary = {
  grossAmount: number
  totalNetAmount: number
  availableBalance: number
  pendingBalance: number
}

export type PayoutRequest = {
  id: number
  teacherId: number | null
  teacherName: string | null
  amount: number
  requestedNote: string | null
  decisionReason: string | null
  payoutStatus: PayoutStatus
  createdAt: string
}

export type RefundRequest = {
  id: number
  orderId: number | null
  orderNumber: string | null
  amount: number
  reason: string
  decisionReason: string | null
  refundStatus: RefundStatus
  createdAt: string
}

export type CommissionSetting = {
  commissionRate: number
  settingKey: string
  description: string
}
