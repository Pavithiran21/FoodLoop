import { useQuery } from '@tanstack/react-query';
import { getDemo } from '../../api/demo';
import { keys } from '../../api/queryClient';

/** Demo mode is on when GET /demo answers (it is a 404 when DEMO_MODE is off). */
export function useDemoAvailable() {
  const { data } = useQuery({ queryKey: keys.demo, queryFn: getDemo, retry: false, staleTime: Infinity });
  return Boolean(data);
}
