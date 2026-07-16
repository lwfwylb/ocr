import { reactive } from 'vue'

export interface TablePageState {
  pageNo: number
  pageSize: number
  total: number
}

export function createTablePage(pageSize = 20) {
  return reactive<TablePageState>({
    pageNo: 1,
    pageSize,
    total: 0
  })
}

export function pageParams(page: TablePageState) {
  return {
    pageNo: page.pageNo,
    pageSize: page.pageSize
  }
}

export function resetPage(page: TablePageState) {
  page.pageNo = 1
}
