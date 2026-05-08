import { publicContentService } from './publicContentService'

export const levelService = {
  async getLevels() {
    return publicContentService.getLevels()
  },
}
