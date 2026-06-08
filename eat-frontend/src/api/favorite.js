import request from './request'

export function addFavoriteApi(params) {
  return request.post('/favorites', params)
}

export function deleteFavoriteApi(id) {
  return request.delete(`/favorites/${id}`)
}

export function getFavoritesApi(params) {
  return request.get('/favorites', { params })
}
