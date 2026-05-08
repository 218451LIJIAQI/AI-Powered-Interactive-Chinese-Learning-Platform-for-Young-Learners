import { publicContentService } from './publicContentService'

export const categoryService = {
  async getCategories() {
    return publicContentService.getCategories()
  },
}
