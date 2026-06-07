import type { JobListResponse, Metrics, Queue, Worker } from '../types';

const TOKEN_KEY = 'scheduler.jwt';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getToken();
  const response = await fetch(path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers
    }
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || `Request failed with status ${response.status}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export async function issueDevToken(subject: string): Promise<string> {
  const response = await request<{ accessToken: string }>('/api/auth/token', {
    method: 'POST',
    body: JSON.stringify({ subject, roles: ['ADMIN', 'OPERATOR', 'VIEWER'] })
  });
  setToken(response.accessToken);
  return response.accessToken;
}

export function fetchMetrics(): Promise<Metrics> {
  return request<Metrics>('/api/metrics');
}

export function fetchJobs(): Promise<JobListResponse> {
  return request<JobListResponse>('/api/jobs?limit=20');
}

export function fetchQueues(): Promise<Queue[]> {
  return request<Queue[]>('/api/queues');
}

export function fetchWorkers(): Promise<Worker[]> {
  return request<Worker[]>('/api/workers');
}

export async function pauseQueue(id: string): Promise<Queue> {
  return request<Queue>(`/api/queues/${id}/pause`, { method: 'PUT' });
}

export async function resumeQueue(id: string): Promise<Queue> {
  return request<Queue>(`/api/queues/${id}/resume`, { method: 'PUT' });
}
